package com.fastapi.codegen.action

import com.fastapi.codegen.db.DatabaseConnector
import com.fastapi.codegen.db.DatabaseSyncer
import com.fastapi.codegen.dialog.DatabaseConfigDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import java.io.File

class SyncDatabaseAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val modelsDir = File(basePath, "models")
        if (!modelsDir.isDirectory || modelsDir.listFiles()?.none { it.name.endsWith(".py") && it.name != "__init__.py" } != false) {
            Messages.showWarningDialog(
                project,
                "No model files found in 'models/' directory.",
                "No Models"
            )
            return
        }

        val configDialog = DatabaseConfigDialog(basePath)
        if (!configDialog.showAndGet()) return

        val connector = configDialog.buildConnector()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Syncing database schema...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val syncer = DatabaseSyncer(connector)
                val result = syncer.executeSync(basePath)

                ApplicationManager.getApplication().invokeLater {
                    val msg = buildString {
                        if (result.createdTables.isNotEmpty()) {
                            appendLine("Created tables: ${result.createdTables.joinToString(", ")}")
                        }
                        if (result.alteredTables.isNotEmpty()) {
                            appendLine("Altered tables: ${result.alteredTables.joinToString(", ")}")
                        }
                        if (result.createdTables.isEmpty() && result.alteredTables.isEmpty()) {
                            appendLine("Database schema is up to date.")
                        }
                        if (result.errors.isNotEmpty()) {
                            appendLine()
                            appendLine("Errors:")
                            result.errors.forEach { appendLine("  $it") }
                        }
                    }

                    if (result.errors.any { it.startsWith("Execution failed") }) {
                        Messages.showErrorDialog(project, msg.trim(), "Sync Failed")
                    } else {
                        Messages.showInfoMessage(project, msg.trim(), "Database Sync Complete")
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
