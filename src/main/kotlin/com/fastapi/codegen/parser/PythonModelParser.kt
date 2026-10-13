package com.fastapi.codegen.parser

import com.fastapi.codegen.model.ColumnInfo
import com.fastapi.codegen.model.TableInfo
import java.io.File

class PythonModelParser {

    enum class ModelType {
        SQLALCHEMY,
        SQLMODEL,
        TORTOISE,
        PEEWEE,
        DJANGO,
        PICCOLO
    }

    companion object {
        private val SQLALCHEMY_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*(\w+)\s*\)\s*:""")
        private val SQLMODEL_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*SQLModel\s*,\s*table\s*=\s*True\s*\)\s*:""")
        private val TORTOISE_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*Model\s*\)\s*:""")
        private val PEEWEE_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*(\w*Model\w*)\s*\)\s*:""")
        private val DJANGO_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*models\.Model\s*\)\s*:""")
        private val PICCOLO_CLASS_PATTERN = Regex("""class\s+(\w+)\s*\(\s*Table\s*\)\s*:""")

        private val TABLENAME_PATTERN = Regex("""__tablename__\s*=\s*["']([^"']+)["']""")
        private val TORTOISE_TABLE_PATTERN = Regex("""table\s*=\s*["']([^"']+)["']""")
        private val DJANGO_TABLE_PATTERN = Regex("""db_table\s*=\s*["']([^"']+)["']""")

        private val SQLALCHEMY_COLUMN_PATTERN = Regex("""(\w+)\s*=\s*Column\(([^)]*)\)""")
        private val SQLMODEL_FIELD_PATTERN = Regex("""(\w+)\s*:\s*([\w\[\]\|]+)\s*=\s*Field\(([^)]*)\)""")
        private val TORTOISE_FIELD_PATTERN = Regex("""(\w+)\s*=\s*fields\.(\w+Field)\(([^)]*)\)""")
        private val PEEWEE_FIELD_PATTERN = Regex("""(\w+)\s*=\s*(\w+Field)\(([^)]*)\)""")
        private val DJANGO_FIELD_PATTERN = Regex("""(\w+)\s*=\s*models\.(\w+Field)\(([^)]*)\)""")
        private val PICCOLO_COLUMN_PATTERN = Regex("""(\w+)\s*=\s*(\w+)\(([^)]*)\)""")

        private val SQLMODEL_TYPE_ONLY_PATTERN = Regex("""(\w+)\s*:\s*([\w\[\]\|]+)(?:\s*=\s*["']?[^"'\n]*["']?)?""")
        private val TYPE_PATTERN = Regex("""(\w+)(?:\(([^)]*)\))?""")

        fun findModelFiles(projectPath: String): List<File> {
            val modelsDir = File(projectPath, "models")
            if (!modelsDir.isDirectory) return emptyList()
            return modelsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".py") && it.name != "__init__.py" }
                .sortedBy { it.absolutePath }
                .toList()
        }

        fun detectOrmType(projectPath: String): ModelType? {
            val requirementsFile = File(projectPath, "requirements.txt")
            if (requirementsFile.exists()) {
                val content = requirementsFile.readText().lowercase()
                when {
                    "sqlmodel" in content -> return ModelType.SQLMODEL
                    "tortoise-orm" in content -> return ModelType.TORTOISE
                    "peewee" in content -> return ModelType.PEEWEE
                    "django" in content -> return ModelType.DJANGO
                    "piccolo" in content -> return ModelType.PICCOLO
                    "sqlalchemy" in content -> return ModelType.SQLALCHEMY
                }
            }

            val pyprojectFile = File(projectPath, "pyproject.toml")
            if (pyprojectFile.exists()) {
                val content = pyprojectFile.readText().lowercase()
                when {
                    "sqlmodel" in content -> return ModelType.SQLMODEL
                    "tortoise" in content -> return ModelType.TORTOISE
                    "peewee" in content -> return ModelType.PEEWEE
                    "django" in content -> return ModelType.DJANGO
                    "piccolo" in content -> return ModelType.PICCOLO
                    "sqlalchemy" in content -> return ModelType.SQLALCHEMY
                }
            }

            val modelFiles = findModelFiles(projectPath)
            for (file in modelFiles) {
                val content = file.readText()
                when {
                    "from sqlmodel" in content || "import sqlmodel" in content -> return ModelType.SQLMODEL
                    "from tortoise" in content || "import tortoise" in content -> return ModelType.TORTOISE
                    "from peewee" in content || "import peewee" in content -> return ModelType.PEEWEE
                    "from django.db" in content || "from django" in content -> return ModelType.DJANGO
                    "from piccolo.table" in content || "from piccolo" in content -> return ModelType.PICCOLO
                    "from sqlalchemy" in content || "import sqlalchemy" in content -> return ModelType.SQLALCHEMY
                }
            }

            return null
        }

        fun parseModelFile(file: File, basePath: String? = null): TableInfo? {
            val content = file.readText()
            val relativePath = basePath?.let {
                file.absolutePath.removePrefix(it).removePrefix(File.separator)
            } ?: file.name

            val sqlmodelMatch = SQLMODEL_CLASS_PATTERN.find(content)
            if (sqlmodelMatch != null) {
                return parseSqlModel(content, sqlmodelMatch.groupValues[1], relativePath)
            }

            val tortoiseMatch = TORTOISE_CLASS_PATTERN.find(content)
            if (tortoiseMatch != null && content.contains("from tortoise")) {
                return parseTortoise(content, tortoiseMatch.groupValues[1], relativePath)
            }

            val djangoMatch = DJANGO_CLASS_PATTERN.find(content)
            if (djangoMatch != null) {
                return parseDjango(content, djangoMatch.groupValues[1], relativePath)
            }

            val piccoloMatch = PICCOLO_CLASS_PATTERN.find(content)
            if (piccoloMatch != null && content.contains("from piccolo")) {
                return parsePiccolo(content, piccoloMatch.groupValues[1], relativePath)
            }

            val peeweeMatch = PEEWEE_CLASS_PATTERN.find(content)
            if (peeweeMatch != null && content.contains("from peewee")) {
                return parsePeewee(content, peeweeMatch.groupValues[1], relativePath)
            }

            val sqlalchemyMatch = SQLALCHEMY_CLASS_PATTERN.find(content)
            if (sqlalchemyMatch != null) {
                return parseSqlAlchemy(content, sqlalchemyMatch.groupValues[1], relativePath)
            }

            return null
        }

        private fun parseSqlAlchemy(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableNameMatch = TABLENAME_PATTERN.find(content)
            val tableName = tableNameMatch?.groupValues?.get(1) ?: toSnakeCase(className)

            val columns = parseSqlAlchemyColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parseSqlModel(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableNameMatch = TABLENAME_PATTERN.find(content)
            val tableName = tableNameMatch?.groupValues?.get(1) ?: toSnakeCase(className)

            val columns = parseSqlModelColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parseTortoise(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableMatch = TORTOISE_TABLE_PATTERN.find(content)
            val tableName = tableMatch?.groupValues?.get(1) ?: toSnakeCase(className)

            val columns = parseTortoiseColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parsePeewee(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableName = toSnakeCase(className)

            val columns = parsePeeweeColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parseDjango(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableMatch = DJANGO_TABLE_PATTERN.find(content)
            val tableName = tableMatch?.groupValues?.get(1) ?: toSnakeCase(className)

            val columns = parseDjangoColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parsePiccolo(content: String, className: String, sourceFile: String? = null): TableInfo? {
            val tableName = toSnakeCase(className)

            val columns = parsePiccoloColumns(content)
            if (columns.isEmpty()) return null

            return TableInfo(tableName, null, columns, sourceFile)
        }

        private fun parseSqlAlchemyColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()
            for (match in SQLALCHEMY_COLUMN_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val argsStr = match.groupValues[2]
                val (typeName, isPrimaryKey, nullable) = parseSqlAlchemyColumnArgs(argsStr)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }
            return columns
        }

        private fun parseSqlModelColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()

            for (match in SQLMODEL_FIELD_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val typeHint = match.groupValues[2]
                val fieldArgs = match.groupValues[3]

                val (typeName, isPrimaryKey, nullable) = parseSqlModelFieldArgs(typeHint, fieldArgs)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }

            if (columns.isEmpty()) {
                for (match in SQLMODEL_TYPE_ONLY_PATTERN.findAll(content)) {
                    val colName = match.groupValues[1]
                    val typeHint = match.groupValues[2]
                    if (colName in listOf("id", "name", "email", "description", "created_at", "updated_at") ||
                        typeHint in listOf("int", "str", "float", "bool", "datetime", "date")) {
                        val (typeName, nullable) = parseSqlModelTypeHint(typeHint)
                        val isPrimaryKey = colName == "id"

                        columns.add(
                            ColumnInfo(
                                name = colName,
                                typeName = typeName,
                                sqlType = mapTypeNameToSqlType(typeName),
                                nullable = nullable,
                                isPrimaryKey = isPrimaryKey,
                                defaultValue = null,
                                comment = null
                            )
                        )
                    }
                }
            }

            return columns
        }

        private fun parseTortoiseColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()
            for (match in TORTOISE_FIELD_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val fieldType = match.groupValues[2]
                val argsStr = match.groupValues[3]

                val (typeName, isPrimaryKey, nullable) = parseTortoiseFieldArgs(fieldType, argsStr)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }
            return columns
        }

        private fun parsePeeweeColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()
            for (match in PEEWEE_FIELD_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val fieldType = match.groupValues[2]
                val argsStr = match.groupValues[3]

                val (typeName, isPrimaryKey, nullable) = parsePeeweeFieldArgs(fieldType, argsStr)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }
            return columns
        }

        private fun parseDjangoColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()
            for (match in DJANGO_FIELD_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val fieldType = match.groupValues[2]
                val argsStr = match.groupValues[3]

                val (typeName, isPrimaryKey, nullable) = parseDjangoFieldArgs(fieldType, argsStr)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }
            return columns
        }

        private fun parsePiccoloColumns(content: String): List<ColumnInfo> {
            val columns = mutableListOf<ColumnInfo>()
            for (match in PICCOLO_COLUMN_PATTERN.findAll(content)) {
                val colName = match.groupValues[1]
                val fieldType = match.groupValues[2]
                val argsStr = match.groupValues[3]

                val (typeName, isPrimaryKey, nullable) = parsePiccoloFieldArgs(fieldType, argsStr)

                columns.add(
                    ColumnInfo(
                        name = colName,
                        typeName = typeName,
                        sqlType = mapTypeNameToSqlType(typeName),
                        nullable = nullable,
                        isPrimaryKey = isPrimaryKey,
                        defaultValue = null,
                        comment = null
                    )
                )
            }
            return columns
        }

        private fun parsePeeweeFieldArgs(fieldType: String, argsStr: String): Triple<String, Boolean, Boolean> {
            val typeName = when (fieldType) {
                "IntegerField", "SmallIntegerField", "BigIntegerField" -> "Integer"
                "CharField", "TextField" -> "String"
                "FloatField", "DecimalField" -> "Float"
                "BooleanField" -> "Boolean"
                "DateField" -> "Date"
                "DateTimeField" -> "DateTime"
                "TimeField" -> "Time"
                "BlobField" -> "LargeBinary"
                "UUIDField" -> "String"
                else -> "String"
            }

            val isPrimaryKey = argsStr.contains("primary_key=True")
            val nullable = argsStr.contains("null=True")

            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parseDjangoFieldArgs(fieldType: String, argsStr: String): Triple<String, Boolean, Boolean> {
            val typeName = when (fieldType) {
                "IntegerField", "SmallIntegerField", "BigIntegerField", "PositiveIntegerField" -> "Integer"
                "CharField", "TextField", "SlugField", "EmailField", "URLField" -> "String"
                "FloatField", "DecimalField" -> "Float"
                "BooleanField", "NullBooleanField" -> "Boolean"
                "DateField" -> "Date"
                "DateTimeField" -> "DateTime"
                "TimeField" -> "Time"
                "BinaryField" -> "LargeBinary"
                "UUIDField" -> "String"
                "JSONField" -> "JSON"
                "FileField", "ImageField" -> "String"
                else -> "String"
            }

            val isPrimaryKey = argsStr.contains("primary_key=True")
            val nullable = argsStr.contains("null=True") || argsStr.contains("blank=True")

            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parsePiccoloFieldArgs(fieldType: String, argsStr: String): Triple<String, Boolean, Boolean> {
            val typeName = when (fieldType) {
                "Integer", "SmallInt", "BigInt" -> "Integer"
                "Varchar", "Text" -> "String"
                "Real", "Numeric", "Decimal" -> "Float"
                "Boolean" -> "Boolean"
                "Date" -> "Date"
                "Timestamp", "Timestamptz" -> "DateTime"
                "Time", "Timetz" -> "Time"
                "Bytea", "Blob" -> "LargeBinary"
                "UUID" -> "String"
                "Json", "Jsonb" -> "JSON"
                else -> "String"
            }

            val isPrimaryKey = argsStr.contains("primary_key=True")
            val nullable = argsStr.contains("null=True") || argsStr.contains("default=None")

            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parseTortoiseFieldArgs(fieldType: String, argsStr: String): Triple<String, Boolean, Boolean> {
            var typeName = when (fieldType) {
                "IntField", "SmallIntField", "BigIntField" -> "Integer"
                "CharField", "TextField" -> "String"
                "FloatField", "DecimalField" -> "Float"
                "BooleanField" -> "Boolean"
                "DateField" -> "Date"
                "DatetimeField", "TimeField" -> "DateTime"
                "JSONField" -> "JSON"
                "BinaryField" -> "LargeBinary"
                else -> "String"
            }

            var isPrimaryKey = argsStr.contains("pk=True")
            var nullable = argsStr.contains("null=True")

            if (isPrimaryKey) nullable = false
            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parseSqlAlchemyColumnArgs(argsStr: String): Triple<String, Boolean, Boolean> {
            val parts = argsStr.split(",").map { it.trim() }
            var typeName = "String"
            var isPrimaryKey = false
            var nullable = true

            for (part in parts) {
                val typeMatch = TYPE_PATTERN.matchEntire(part)
                if (typeMatch != null && part[0].isUpperCase()) {
                    typeName = typeMatch.groupValues[1]
                } else when {
                    part == "primary_key=True" -> isPrimaryKey = true
                    part == "nullable=False" -> nullable = false
                    part == "nullable=True" -> nullable = true
                }
            }

            if (isPrimaryKey) nullable = false
            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parseSqlModelFieldArgs(typeHint: String, fieldArgs: String): Triple<String, Boolean, Boolean> {
            val (typeName, nullableFromType) = parseSqlModelTypeHint(typeHint)
            var isPrimaryKey = false
            var nullable = nullableFromType

            for (part in fieldArgs.split(",").map { it.trim() }) {
                when {
                    part == "primary_key=True" -> isPrimaryKey = true
                    part == "nullable=False" -> nullable = false
                    part == "nullable=True" -> nullable = true
                    part.startsWith("max_length=") -> {}
                }
            }

            if (isPrimaryKey) nullable = false
            return Triple(typeName, isPrimaryKey, nullable)
        }

        private fun parseSqlModelTypeHint(typeHint: String): Pair<String, Boolean> {
            val cleanType = typeHint.replace(" ", "")
            val nullable = cleanType.contains("|None") || cleanType.startsWith("Optional[")

            val baseType = cleanType
                .replace("|None", "")
                .replace("Optional[", "")
                .replace("]", "")

            val typeName = when (baseType.lowercase()) {
                "int" -> "Integer"
                "str" -> "String"
                "float" -> "Float"
                "bool" -> "Boolean"
                "datetime" -> "DateTime"
                "date" -> "Date"
                "time" -> "Time"
                "bytes" -> "LargeBinary"
                else -> "String"
            }

            return Pair(typeName, nullable)
        }

        private fun mapTypeNameToSqlType(typeName: String): Int {
            return when (typeName.uppercase()) {
                "INTEGER", "INT", "SMALLINT", "TINYINT" -> java.sql.Types.INTEGER
                "BIGINT" -> java.sql.Types.BIGINT
                "FLOAT" -> java.sql.Types.FLOAT
                "DOUBLE", "DECIMAL", "NUMERIC" -> java.sql.Types.DOUBLE
                "VARCHAR", "CHAR", "STRING" -> java.sql.Types.VARCHAR
                "TEXT" -> java.sql.Types.CLOB
                "BOOLEAN", "BOOL" -> java.sql.Types.BOOLEAN
                "DATE" -> java.sql.Types.DATE
                "DATETIME", "TIMESTAMP" -> java.sql.Types.TIMESTAMP
                "TIME" -> java.sql.Types.TIME
                "BLOB", "BINARY", "LARGEBINARY" -> java.sql.Types.BLOB
                "JSON" -> java.sql.Types.OTHER
                else -> java.sql.Types.VARCHAR
            }
        }

        private fun toSnakeCase(name: String): String {
            return name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        }
    }
}
