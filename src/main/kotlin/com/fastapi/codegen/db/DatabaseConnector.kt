package com.fastapi.codegen.db

import com.fastapi.codegen.model.ColumnInfo
import com.fastapi.codegen.model.TableInfo
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

class DatabaseConnector(
    val dbType: DbType,
    private val host: String,
    private val port: Int,
    private val database: String,
    private val username: String,
    private val password: String,
    private val useSSL: Boolean = false
) {
    enum class DbType(val driverClass: String, val defaultPort: Int) {
        MYSQL("com.mysql.cj.jdbc.Driver", 3306),
        POSTGRESQL("org.postgresql.Driver", 5432);

        fun buildUrl(host: String, port: Int, database: String, useSSL: Boolean): String = when (this) {
            MYSQL -> {
                val sslParam = if (useSSL) "useSSL=true&requireSSL=true" else "useSSL=false&allowPublicKeyRetrieval=true"
                "jdbc:mysql://$host:$port/$database?$sslParam&serverTimezone=UTC"
            }
            POSTGRESQL -> {
                val sslParam = if (useSSL) "?sslmode=prefer" else "?sslmode=disable"
                "jdbc:postgresql://$host:$port/$database$sslParam"
            }
        }
    }

    private fun getConnection(): Connection {
        Class.forName(dbType.driverClass)
        val url = dbType.buildUrl(host, port, database, useSSL)
        return DriverManager.getConnection(url, username, password)
    }

    fun testConnection(): Result<Boolean> {
        return try {
            getConnection().use { it.isValid(5) }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun executeSql(statements: List<String>) {
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                val stmt = conn.createStatement()
                for (sql in statements) {
                    stmt.execute(sql)
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun getTables(): List<TableInfo> {
        getConnection().use { conn ->
            val meta = conn.metaData
            val tables = mutableListOf<TableInfo>()

            meta.getTables(database, null, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    val tableName = rs.getString("TABLE_NAME")
                    val tableComment = rs.getString("REMARKS")
                    val columns = getColumns(conn, tableName)
                    tables.add(TableInfo(tableName, tableComment, columns))
                }
            }
            return tables
        }
    }

    private fun getColumns(conn: Connection, tableName: String): List<ColumnInfo> {
        val meta = conn.metaData
        val primaryKeys = getPrimaryKeys(meta, tableName)
        val columns = mutableListOf<ColumnInfo>()

        meta.getColumns(database, null, tableName, "%").use { rs ->
            while (rs.next()) {
                columns.add(
                    ColumnInfo(
                        name = rs.getString("COLUMN_NAME"),
                        typeName = rs.getString("TYPE_NAME"),
                        sqlType = rs.getInt("DATA_TYPE"),
                        nullable = rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable,
                        isPrimaryKey = rs.getString("COLUMN_NAME") in primaryKeys,
                        defaultValue = rs.getString("COLUMN_DEF"),
                        comment = rs.getString("REMARKS")
                    )
                )
            }
        }
        return columns
    }

    private fun getPrimaryKeys(meta: java.sql.DatabaseMetaData, tableName: String): Set<String> {
        val keys = mutableSetOf<String>()
        meta.getPrimaryKeys(database, null, tableName).use { rs ->
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"))
            }
        }
        return keys
    }

    companion object {
        fun sqlTypeToPython(sqlType: Int, typeName: String): String {
            return when (sqlType) {
                Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer"
                Types.BIGINT -> "Integer"
                Types.FLOAT, Types.REAL -> "Float"
                Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> "Float"
                Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR -> "String"
                Types.BOOLEAN, Types.BIT -> "Boolean"
                Types.DATE -> "date"
                Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "datetime"
                Types.TIME -> "time"
                Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> "LargeBinary"
                Types.CLOB, Types.NCLOB -> "Text"
                else -> "String"
            }
        }

        fun sqlTypeToSQLAlchemy(sqlType: Int, typeName: String): String {
            val upper = typeName.uppercase()
            return when {
                upper.contains("BIGINT") -> "BigInteger"
                upper.contains("SMALLINT") -> "SmallInteger"
                upper.contains("TINYINT") -> "SmallInteger"
                upper.contains("INT") -> "Integer"
                upper.contains("FLOAT") || upper.contains("DOUBLE") -> "Float"
                upper.contains("DECIMAL") || upper.contains("NUMERIC") -> "Numeric"
                upper.contains("VARCHAR") || upper.contains("CHAR") -> "String"
                upper.contains("TEXT") -> "Text"
                upper.contains("BOOL") -> "Boolean"
                upper.contains("DATE") && !upper.contains("TIME") -> "Date"
                upper.contains("TIMESTAMP") || upper.contains("DATETIME") -> "DateTime"
                upper.contains("TIME") -> "Time"
                upper.contains("BLOB") || upper.contains("BINARY") -> "LargeBinary"
                upper.contains("JSON") -> "JSON"
                else -> sqlTypeToPython(sqlType, typeName).let {
                    when (it) {
                        "String" -> "String"
                        "Integer" -> "Integer"
                        "Float" -> "Float"
                        "Boolean" -> "Boolean"
                        else -> "String"
                    }
                }
            }
        }
    }
}
