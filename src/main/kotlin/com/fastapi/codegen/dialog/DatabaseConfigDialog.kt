package com.fastapi.codegen.dialog

import com.fastapi.codegen.db.DatabaseConnector
import com.fastapi.codegen.db.EnvFileParser
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import java.io.File
import javax.swing.JComponent

class DatabaseConfigDialog(private val projectPath: String? = null) : DialogWrapper(true) {

    private var selectedDbType = DatabaseConnector.DbType.MYSQL
    private var inputHost = "localhost"
    private var inputPort = 3306
    private var inputDatabase = ""
    private var inputUsername = "root"
    private var inputPassword = ""
    private var inputUseSSL = false

    private val envFiles: List<File>
    private var selectedEnvIndex = 0

    var dbType: DatabaseConnector.DbType = DatabaseConnector.DbType.MYSQL
        private set
    var host: String = "localhost"
        private set
    var port: Int = 3306
        private set
    var database: String = ""
        private set
    var username: String = "root"
        private set
    var password: String = ""
        private set
    var useSSL: Boolean = false
        private set

    init {
        title = "Database Connection"
        envFiles = if (projectPath != null) EnvFileParser.findEnvFiles(projectPath) else emptyList()
        if (envFiles.isNotEmpty()) {
            loadFromEnv(0)
        }
        init()
    }

    private fun loadFromEnv(index: Int) {
        if (index < 0 || index >= envFiles.size) return
        selectedEnvIndex = index
        val config = EnvFileParser.parse(envFiles[index])
        config.dbType?.let { selectedDbType = it }
        config.host?.let { inputHost = it }
        config.port?.let { inputPort = it }
        config.database?.let { inputDatabase = it }
        config.username?.let { inputUsername = it }
        config.password?.let { inputPassword = it }
    }

    private fun envFileLabels(): List<String> {
        if (envFiles.isEmpty()) return listOf("(No .env file found)")
        return envFiles.map { it.name }
    }

    override fun createCenterPanel(): JComponent {
        val envLabels = envFileLabels()

        return panel {
            if (envFiles.isNotEmpty()) {
                row(".env File:") {
                    comboBox(envLabels)
                        .applyToComponent {
                            selectedIndex = selectedEnvIndex
                            addActionListener {
                                loadFromEnv(selectedIndex)
                            }
                        }
                }
            }
            row("Database Type:") {
                comboBox(DatabaseConnector.DbType.entries.toList())
            }
            row("Host:") {
                textField()
                    .bindText(::inputHost)
                    .columns(30)
            }
            row("Port:") {
                spinner(1..65535, 1)
                    .bindIntValue(::inputPort)
            }
            row("Database:") {
                textField()
                    .bindText(::inputDatabase)
                    .columns(30)
            }
            row("Username:") {
                textField()
                    .bindText(::inputUsername)
                    .columns(20)
            }
            row("Password:") {
                passwordField()
                    .bindText(::inputPassword)
                    .columns(20)
            }
            row {
                checkBox("Use SSL")
                    .bindSelected(::inputUseSSL)
            }
            row {
                button("Test Connection") {
                    testConnection()
                }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (inputDatabase.isBlank()) return ValidationInfo("Database name is required")
        if (inputHost.isBlank()) return ValidationInfo("Host is required")
        return null
    }

    override fun doOKAction() {
        dbType = selectedDbType
        host = inputHost
        port = inputPort
        database = inputDatabase
        username = inputUsername
        password = inputPassword
        useSSL = inputUseSSL
        super.doOKAction()
    }

    private fun testConnection() {
        val connector = DatabaseConnector(selectedDbType, inputHost, inputPort, inputDatabase, inputUsername, inputPassword, inputUseSSL)
        val result = connector.testConnection()
        result.fold(
            onSuccess = { Messages.showInfoMessage("Connection successful!", "Test Connection") },
            onFailure = { e ->
                val msg = buildString {
                    appendLine("Connection failed:")
                    appendLine(e.message ?: "Unknown error")
                    if (e.cause != null && e.cause?.message != null) {
                        appendLine("Cause: ${e.cause?.message}")
                    }
                }
                Messages.showErrorDialog(msg.trim(), "Test Connection")
            }
        )
    }

    fun buildConnector(): DatabaseConnector {
        return DatabaseConnector(dbType, host, port, database, username, password, useSSL)
    }
}
