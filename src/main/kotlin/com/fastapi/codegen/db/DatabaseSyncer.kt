package com.fastapi.codegen.db

import com.fastapi.codegen.model.ColumnInfo
import com.fastapi.codegen.model.TableInfo
import com.fastapi.codegen.parser.PythonModelParser
import java.io.File

class DatabaseSyncer(
    private val connector: DatabaseConnector
) {
    data class SyncResult(
        val createdTables: List<String>,
        val alteredTables: List<String>,
        val sqlStatements: List<String>,
        val errors: List<String>
    )

    fun sync(projectPath: String): SyncResult {
        val modelFiles = PythonModelParser.findModelFiles(projectPath)
        if (modelFiles.isEmpty()) {
            return SyncResult(emptyList(), emptyList(), emptyList(), listOf("No model files found"))
        }

        val models = mutableListOf<TableInfo>()
        val errors = mutableListOf<String>()

        for (file in modelFiles) {
            try {
                val table = PythonModelParser.parseModelFile(file)
                if (table != null) models.add(table)
            } catch (e: Exception) {
                errors.add("${file.name}: ${e.message}")
            }
        }

        if (models.isEmpty()) {
            return SyncResult(emptyList(), emptyList(), emptyList(), errors + "No valid models parsed")
        }

        val existingTables = try {
            connector.getTables().associateBy { it.name }
        } catch (e: Exception) {
            return SyncResult(emptyList(), emptyList(), emptyList(), errors + "Failed to read database: ${e.message}")
        }

        val createdTables = mutableListOf<String>()
        val alteredTables = mutableListOf<String>()
        val sqlStatements = mutableListOf<String>()

        for (model in models) {
            val existing = existingTables[model.name]
            if (existing == null) {
                val sql = generateCreateTable(model)
                sqlStatements.add(sql)
                createdTables.add(model.name)
            } else {
                val alters = generateAlterTable(model, existing)
                if (alters.isNotEmpty()) {
                    sqlStatements.addAll(alters)
                    alteredTables.add(model.name)
                }
            }
        }

        return SyncResult(createdTables, alteredTables, sqlStatements, errors)
    }

    fun syncSelected(selectedTables: List<TableInfo>): SyncResult {
        if (selectedTables.isEmpty()) {
            return SyncResult(emptyList(), emptyList(), emptyList(), listOf("No tables selected"))
        }

        val existingTables = try {
            connector.getTables().associateBy { it.name }
        } catch (e: Exception) {
            return SyncResult(emptyList(), emptyList(), emptyList(), listOf("Failed to read database: ${e.message}"))
        }

        val createdTables = mutableListOf<String>()
        val alteredTables = mutableListOf<String>()
        val sqlStatements = mutableListOf<String>()

        for (model in selectedTables) {
            val existing = existingTables[model.name]
            if (existing == null) {
                val sql = generateCreateTable(model)
                sqlStatements.add(sql)
                createdTables.add(model.name)
            } else {
                val alters = generateAlterTable(model, existing)
                if (alters.isNotEmpty()) {
                    sqlStatements.addAll(alters)
                    alteredTables.add(model.name)
                }
            }
        }

        if (sqlStatements.isNotEmpty()) {
            try {
                connector.executeSql(sqlStatements)
            } catch (e: Exception) {
                return SyncResult(createdTables, alteredTables, sqlStatements, listOf("Execution failed: ${e.message}"))
            }
        }

        return SyncResult(createdTables, alteredTables, sqlStatements, emptyList())
    }

    fun executeSync(projectPath: String): SyncResult {
        val result = sync(projectPath)
        if (result.sqlStatements.isEmpty()) return result

        try {
            connector.executeSql(result.sqlStatements)
        } catch (e: Exception) {
            return result.copy(errors = result.errors + "Execution failed: ${e.message}")
        }
        return result
    }

    private fun generateCreateTable(table: TableInfo): String {
        val dbType = connector.dbType
        val columns = table.columns.joinToString(",\n    ") { col ->
            val sqlType = DatabaseConnector.sqlTypeToSQLAlchemy(col.sqlType, col.typeName)
            val parts = mutableListOf(sqlType)
            if (col.isPrimaryKey) parts.add("PRIMARY KEY")
            if (!col.nullable && !col.isPrimaryKey) parts.add("NOT NULL")
            "${col.name} ${parts.joinToString(" ")}"
        }
        return "CREATE TABLE ${table.name} (\n    $columns\n);"
    }

    private fun generateAlterTable(newTable: TableInfo, existingTable: TableInfo): List<String> {
        val alters = mutableListOf<String>()
        val existingColumns = existingTable.columns.associateBy { it.name.lowercase() }

        for (col in newTable.columns) {
            if (col.name.lowercase() !in existingColumns) {
                val sqlType = DatabaseConnector.sqlTypeToSQLAlchemy(col.sqlType, col.typeName)
                val nullable = if (col.nullable) "" else " NOT NULL"
                alters.add("ALTER TABLE ${newTable.name} ADD COLUMN ${col.name} $sqlType$nullable;")
            }
        }

        return alters
    }
}
