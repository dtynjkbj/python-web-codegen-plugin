package com.fastapi.codegen.db

import java.io.File

class EnvFileParser {

    data class DbConfig(
        val host: String?,
        val port: Int?,
        val database: String?,
        val username: String?,
        val password: String?,
        val dbType: DatabaseConnector.DbType?
    )

    companion object {
        private val ENV_FILE_NAMES = listOf(".env", ".env.local", ".env.development", ".env.dev", ".env.test", ".env.production", ".env.prod")

        private val DB_HOST_KEYS = listOf("DB_HOST", "DATABASE_HOST", "MYSQL_HOST", "POSTGRES_HOST", "PG_HOST")
        private val DB_PORT_KEYS = listOf("DB_PORT", "DATABASE_PORT", "MYSQL_PORT", "POSTGRES_PORT", "PG_PORT")
        private val DB_NAME_KEYS = listOf("DB_NAME", "DATABASE_NAME", "MYSQL_DB", "POSTGRES_DB", "PG_DB", "DB_DATABASE")
        private val DB_USER_KEYS = listOf("DB_USER", "DATABASE_USER", "MYSQL_USER", "POSTGRES_USER", "PG_USER", "DB_USERNAME")
        private val DB_PASS_KEYS = listOf("DB_PASS", "DB_PASSWORD", "DATABASE_PASSWORD", "MYSQL_PASSWORD", "POSTGRES_PASSWORD", "PG_PASSWORD")
        private val DB_TYPE_KEYS = listOf("DB_TYPE", "DATABASE_TYPE", "DB_ENGINE")

        fun findEnvFiles(projectPath: String): List<File> {
            val dir = File(projectPath)
            if (!dir.isDirectory) return emptyList()
            return ENV_FILE_NAMES.mapNotNull { name ->
                val f = File(dir, name)
                if (f.exists()) f else null
            }
        }

        fun parse(envFile: File): DbConfig {
            if (!envFile.exists()) return DbConfig(null, null, null, null, null, null)

            val props = mutableMapOf<String, String>()
            envFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex > 0) {
                    val key = trimmed.substring(0, eqIndex).trim()
                    var value = trimmed.substring(eqIndex + 1).trim()
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length - 1)
                    }
                    props[key.uppercase()] = value
                }
            }

            val host = DB_HOST_KEYS.firstOrNull { props.containsKey(it) }?.let { props[it] }
            val port = DB_PORT_KEYS.firstOrNull { props.containsKey(it) }?.let { props[it]?.toIntOrNull() }
            val database = DB_NAME_KEYS.firstOrNull { props.containsKey(it) }?.let { props[it] }
            val username = DB_USER_KEYS.firstOrNull { props.containsKey(it) }?.let { props[it] }
            val password = DB_PASS_KEYS.firstOrNull { props.containsKey(it) }?.let { props[it] }

            val dbType = DB_TYPE_KEYS.firstOrNull { props.containsKey(it) }?.let { key ->
                when (props[key]?.lowercase()) {
                    "mysql" -> DatabaseConnector.DbType.MYSQL
                    "postgresql", "postgres", "pg" -> DatabaseConnector.DbType.POSTGRESQL
                    else -> null
                }
            }

            return DbConfig(host, port, database, username, password, dbType)
        }
    }
}
