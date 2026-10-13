package com.fastapi.codegen.action

import com.fastapi.codegen.db.DatabaseSyncer
import com.fastapi.codegen.dialog.DatabaseConfigDialog
import com.fastapi.codegen.dialog.GenerateOptionsDialog
import com.fastapi.codegen.dialog.GenerateOptionsDialog.GenerationTarget
import com.fastapi.codegen.dialog.GenerationModeDialog
import com.fastapi.codegen.dialog.TableSelectionDialog
import com.fastapi.codegen.generator.CodeGenerator
import com.fastapi.codegen.model.TableInfo
import com.fastapi.codegen.parser.PythonModelParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class GenerateCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val modeDialog = GenerationModeDialog()
        if (!modeDialog.showAndGet()) return

        if (modeDialog.useDatabase) {
            generateFromDatabase(project, basePath)
        } else {
            generateFromModels(project, basePath)
        }
    }

    private fun generateFromModels(project: com.intellij.openapi.project.Project, basePath: String) {
        val modelFiles = PythonModelParser.findModelFiles(basePath)
        if (modelFiles.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "No model files found in 'models/' directory.\nPlease create SQLAlchemy/SQLModel/Tortoise models first.",
                "No Models Found"
            )
            return
        }

        val tables = mutableListOf<TableInfo>()
        val parseErrors = mutableListOf<String>()

        for (file in modelFiles) {
            try {
                val table = PythonModelParser.parseModelFile(file, basePath)
                if (table != null) {
                    tables.add(table)
                } else {
                    parseErrors.add("${file.name}: no valid model class found")
                }
            } catch (ex: Exception) {
                parseErrors.add("${file.name}: ${ex.message}")
            }
        }

        if (tables.isEmpty()) {
            val errorMsg = buildString {
                appendLine("Failed to parse any model files:")
                parseErrors.forEach { appendLine("  $it") }
            }
            Messages.showErrorDialog(project, errorMsg.trim(), "Parse Error")
            return
        }

        val selectionDialog = TableSelectionDialog(tables, basePath)
        if (!selectionDialog.showAndGet()) return

        val selectedTables = selectionDialog.selectedTables
        if (selectedTables.isEmpty()) {
            Messages.showInfoMessage(project, "No models selected.", "Info")
            return
        }

        val optionsDialog = GenerateOptionsDialog()
        if (!optionsDialog.showAndGet()) return

        val targets = optionsDialog.selectedTargets
        if (targets.isEmpty()) return

        if (GenerationTarget.SYNC_DB in targets) {
            runDatabaseSync(project, basePath, selectedTables)
        }

        val codeTargets = targets - GenerationTarget.SYNC_DB
        if (codeTargets.isNotEmpty()) {
            generateCodeFiles(project, basePath, selectedTables, codeTargets, parseErrors)
        }
    }

    private fun generateFromDatabase(project: com.intellij.openapi.project.Project, basePath: String) {
        val configDialog = DatabaseConfigDialog(basePath)
        if (!configDialog.showAndGet()) return

        val connector = configDialog.buildConnector()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching database tables...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val tables: List<TableInfo> = try {
                    connector.getTables()
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Failed to fetch tables: ${ex.message}", "Error")
                    }
                    return
                }

                if (tables.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "No tables found in the database.", "Info")
                    }
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    showTableSelectionForDatabase(project, basePath, tables)
                }
            }
        })
    }

    private fun showTableSelectionForDatabase(
        project: com.intellij.openapi.project.Project,
        basePath: String,
        tables: List<TableInfo>
    ) {
        val selectionDialog = TableSelectionDialog(tables, basePath)
        if (!selectionDialog.showAndGet()) return

        val selectedTables = selectionDialog.selectedTables
        if (selectedTables.isEmpty()) {
            Messages.showInfoMessage(project, "No tables selected.", "Info")
            return
        }

        val optionsDialog = GenerateOptionsDialog()
        if (!optionsDialog.showAndGet()) return

        val targets = optionsDialog.selectedTargets
        if (targets.isEmpty()) return

        val codeTargets = targets - GenerationTarget.SYNC_DB
        if (codeTargets.isNotEmpty()) {
            generateCodeFiles(project, basePath, selectedTables, codeTargets, emptyList())
        }
    }

    private fun generateCodeFiles(
        project: com.intellij.openapi.project.Project,
        basePath: String,
        tables: List<TableInfo>,
        targets: Set<GenerationTarget>,
        parseErrors: List<String>
    ) {
        val generator = CodeGenerator()
        val dirsToCreate = mutableListOf<String>()

        if (GenerationTarget.MODELS in targets) dirsToCreate.add("models")
        if (GenerationTarget.SCHEMAS in targets) dirsToCreate.add("schemas")
        if (GenerationTarget.SERVICES in targets) dirsToCreate.add("services")
        if (GenerationTarget.ROUTERS in targets) dirsToCreate.add("routers")

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val generatedFiles = mutableListOf<String>()

                for (table in tables) {
                    val snakeName = table.snakeName

                    if (GenerationTarget.MODELS in targets) {
                        val content = generator.generateModel(table)
                        writeFile(basePath, "models", "$snakeName.py", content)
                        generatedFiles.add("models/$snakeName.py")
                    }

                    if (GenerationTarget.SCHEMAS in targets) {
                        val content = generator.generateSchema(table)
                        writeFile(basePath, "schemas", "$snakeName.py", content)
                        generatedFiles.add("schemas/$snakeName.py")
                    }

                    if (GenerationTarget.SERVICES in targets) {
                        val content = generator.generateService(table)
                        writeFile(basePath, "services", "$snakeName.py", content)
                        generatedFiles.add("services/$snakeName.py")
                    }

                    if (GenerationTarget.ROUTERS in targets) {
                        val content = generator.generateRouter(table)
                        writeFile(basePath, "routers", "$snakeName.py", content)
                        generatedFiles.add("routers/$snakeName.py")
                    }
                }

                for (dir in dirsToCreate) {
                    ensureInitFiles(basePath, dir)
                }

                refreshDirectories(basePath, dirsToCreate)

                val msg = buildString {
                    appendLine("Generated ${generatedFiles.size} file(s) for ${tables.size} model(s):")
                    appendLine()
                    generatedFiles.forEach { appendLine("  $it") }
                    if (parseErrors.isNotEmpty()) {
                        appendLine()
                        appendLine("Warnings:")
                        parseErrors.forEach { appendLine("  $it") }
                    }
                }
                Messages.showInfoMessage(project, msg, "Code Generation Complete")
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Code generation failed: ${ex.message}", "Error")
            }
        }
    }

    private fun runDatabaseSync(
        project: com.intellij.openapi.project.Project,
        basePath: String,
        selectedTables: List<TableInfo>
    ) {
        val configDialog = DatabaseConfigDialog(basePath)
        if (!configDialog.showAndGet()) return

        val connector = configDialog.buildConnector()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Syncing database schema...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val syncer = DatabaseSyncer(connector)
                val result = try {
                    syncer.syncSelected(selectedTables)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Database sync failed: ${ex.message}", "Error")
                    }
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    val msg = buildString {
                        if (result.createdTables.isNotEmpty()) {
                            appendLine("Created tables:")
                            result.createdTables.forEach { appendLine("  $it") }
                        }
                        if (result.alteredTables.isNotEmpty()) {
                            appendLine("Altered tables:")
                            result.alteredTables.forEach { appendLine("  $it") }
                        }
                        if (result.createdTables.isEmpty() && result.alteredTables.isEmpty()) {
                            appendLine("Database is already up to date.")
                        }
                        if (result.errors.isNotEmpty()) {
                            appendLine()
                            appendLine("Warnings:")
                            result.errors.forEach { appendLine("  $it") }
                        }
                    }
                    Messages.showInfoMessage(project, msg.trim(), "Database Sync Complete")
                }
            }
        })
    }

    private fun writeFile(basePath: String, dir: String, fileName: String, content: String) {
        val dirPath = File(basePath, dir)
        if (!dirPath.exists()) dirPath.mkdirs()

        val file = File(dirPath, fileName)
        file.writeText(content)
    }

    private fun ensureInitFiles(basePath: String, dir: String) {
        val initFile = File(File(basePath, dir), "__init__.py")
        if (!initFile.exists()) {
            initFile.writeText("")
        }
    }

    private fun refreshDirectories(basePath: String, dirs: List<String>) {
        val lfs = LocalFileSystem.getInstance()
        for (dir in dirs) {
            val dirFile = File(basePath, dir)
            if (dirFile.exists()) {
                lfs.refreshAndFindFileByIoFile(dirFile)?.refresh(true, true)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
