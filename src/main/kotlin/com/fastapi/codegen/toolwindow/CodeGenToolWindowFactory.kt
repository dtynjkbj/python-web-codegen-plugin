package com.fastapi.codegen.toolwindow

import com.fastapi.codegen.db.DatabaseConnector
import com.fastapi.codegen.db.DatabaseSyncer
import com.fastapi.codegen.dialog.DatabaseConfigDialog
import com.fastapi.codegen.dialog.GenerateOptionsDialog.GenerationTarget
import com.fastapi.codegen.generator.CodeGenerator
import com.fastapi.codegen.i18n.Messages
import com.fastapi.codegen.model.TableInfo
import com.fastapi.codegen.parser.PythonModelParser
import com.fastapi.codegen.parser.PythonModelParser.ModelType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages as UIMessages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import javax.swing.*

class CodeGenToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodeGenPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

class CodeGenPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val basePath = project.basePath ?: ""
    private var tables: List<TableInfo> = emptyList()
    private var selectedTargets: Set<GenerationTarget> = setOf(
        GenerationTarget.SCHEMAS,
        GenerationTarget.SERVICES,
        GenerationTarget.ROUTERS
    )

    private val modeCombo = JComboBox(arrayOf(Messages.get("mode_from_models"), Messages.get("mode_from_database")))
    private val ormCombo = JComboBox(arrayOf("Auto", "SQLAlchemy", "SQLModel", "Tortoise", "Peewee", "Django", "Piccolo"))
    private val frameworkCombo = JComboBox(arrayOf("Auto", "FastAPI", "Flask", "Django REST", "Sanic", "Starlette"))
    private val loadBtn = JButton(Messages.get("load_btn"))
    private val statusLabel = JLabel(Messages.get("status_ready"))
    private val tableCheckPanel = JPanel()
    private val targetPanel = JPanel()

    init {
        tableCheckPanel.layout = BoxLayout(tableCheckPanel, BoxLayout.Y_AXIS)
        targetPanel.layout = BoxLayout(targetPanel, BoxLayout.Y_AXIS)

        val detectedOrm = PythonModelParser.detectOrmType(basePath)
        if (detectedOrm != null) {
            val ormIndex = when (detectedOrm) {
                ModelType.SQLALCHEMY -> 1
                ModelType.SQLMODEL -> 2
                ModelType.TORTOISE -> 3
                ModelType.PEEWEE -> 4
                ModelType.DJANGO -> 5
                ModelType.PICCOLO -> 6
            }
            ormCombo.selectedIndex = ormIndex
        }

        val detectedContext = CodeGenerator.detectProjectContext(basePath)
        val frameworkIndex = when (detectedContext.framework) {
            CodeGenerator.WebFramework.FASTAPI -> 1
            CodeGenerator.WebFramework.FLASK -> 2
            CodeGenerator.WebFramework.DJANGO_REST -> 3
            CodeGenerator.WebFramework.SANIC -> 4
            CodeGenerator.WebFramework.STARLETTE -> 5
        }
        frameworkCombo.selectedIndex = frameworkIndex

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        topPanel.add(JLabel(Messages.get("mode_label")))
        topPanel.add(modeCombo)
        topPanel.add(JLabel(" ORM:"))
        topPanel.add(ormCombo)
        topPanel.add(JLabel(" " + Messages.get("framework_label")))
        topPanel.add(frameworkCombo)
        topPanel.add(loadBtn)
        topPanel.add(statusLabel)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.topComponent = JBScrollPane(tableCheckPanel)
        splitPane.bottomComponent = JBScrollPane(targetPanel)
        splitPane.dividerLocation = 300
        splitPane.resizeWeight = 0.7

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val generateBtn = JButton(Messages.get("generate_btn"))
        buttonPanel.add(generateBtn)

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)

        loadBtn.addActionListener { onLoadClicked() }
        generateBtn.addActionListener { onGenerateClicked() }

        updateTargetPanel()
    }

    private fun onLoadClicked() {
        if (modeCombo.selectedIndex == 0) {
            loadFromModels()
        } else {
            loadFromDatabase()
        }
    }

    private fun loadFromModels() {
        val modelFiles = PythonModelParser.findModelFiles(basePath)
        if (modelFiles.isEmpty()) {
            UIMessages.showWarningDialog(project, Messages.get("msg_no_models"), Messages.get("dialog_title_info"))
            return
        }

        val parsedTables = mutableListOf<TableInfo>()
        val errors = mutableListOf<String>()

        for (file in modelFiles) {
            try {
                val table = PythonModelParser.parseModelFile(file, basePath)
                if (table != null) parsedTables.add(table)
                else errors.add("${file.name}: 未找到有效模型类")
            } catch (ex: Exception) {
                errors.add("${file.name}: ${ex.message}")
            }
        }

        if (parsedTables.isEmpty()) {
            UIMessages.showErrorDialog(project, Messages.get("msg_parse_error").format(errors.joinToString("\n")), Messages.get("dialog_title_error"))
            return
        }

        tables = parsedTables
        updateTablePanel()
        statusLabel.text = Messages.get("status_loaded_models").format(tables.size)
    }

    private fun loadFromDatabase() {
        val configDialog = DatabaseConfigDialog(basePath)
        if (!configDialog.showAndGet()) return

        val connector = configDialog.buildConnector()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, Messages.get("task_fetch_tables"), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val fetchedTables: List<TableInfo> = try {
                    connector.getTables()
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        UIMessages.showErrorDialog(project, Messages.get("msg_fetch_error").format(ex.message ?: ""), Messages.get("dialog_title_error"))
                    }
                    return
                }

                if (fetchedTables.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        UIMessages.showInfoMessage(project, Messages.get("msg_no_tables"), Messages.get("dialog_title_info"))
                    }
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    tables = fetchedTables
                    updateTablePanel()
                    statusLabel.text = Messages.get("status_loaded_tables").format(tables.size)
                }
            }
        })
    }

    private fun updateTablePanel() {
        tableCheckPanel.removeAll()

        val selectAllBtn = JButton(Messages.get("select_all"))
        val deselectAllBtn = JButton(Messages.get("deselect_all"))

        selectAllBtn.addActionListener { tableCheckPanel.components.filterIsInstance<JBCheckBox>().forEach { it.isSelected = true } }
        deselectAllBtn.addActionListener { tableCheckPanel.components.filterIsInstance<JBCheckBox>().forEach { it.isSelected = false } }

        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        btnPanel.add(selectAllBtn)
        btnPanel.add(deselectAllBtn)
        btnPanel.maximumSize = java.awt.Dimension(Int.MAX_VALUE, 30)
        btnPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        tableCheckPanel.add(btnPanel)

        val colSuffix = Messages.get("columns_suffix")

        for (table in tables) {
            val shortPath = table.sourceFile?.let {
                val file = File(it)
                file.parentFile?.name?.let { parent -> "$parent/${file.name}" } ?: file.name
            } ?: table.name

            val label = buildString {
                append("[$shortPath] ")
                append(table.name)
                table.comment?.let { append(" ($it)") }
                append(" [${table.columns.size} $colSuffix]")
            }
            val cb = JBCheckBox(label, true)
            cb.maximumSize = java.awt.Dimension(Int.MAX_VALUE, 25)
            cb.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            tableCheckPanel.add(cb)
        }

        // Add filler to push content to top
        val filler = JPanel()
        filler.maximumSize = java.awt.Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        tableCheckPanel.add(filler)

        tableCheckPanel.revalidate()
        tableCheckPanel.repaint()
    }

    private fun updateTargetPanel() {
        targetPanel.removeAll()

        val header = JLabel(Messages.get("targets_header"))
        header.font = header.font.deriveFont(java.awt.Font.BOLD)
        header.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        targetPanel.add(header)

        val targetLabels = mapOf(
            GenerationTarget.MODELS to Messages.get("target_models"),
            GenerationTarget.SCHEMAS to Messages.get("target_schemas"),
            GenerationTarget.SERVICES to Messages.get("target_services"),
            GenerationTarget.ROUTERS to Messages.get("target_routers"),
            GenerationTarget.SYNC_DB to Messages.get("target_sync_db")
        )

        for (target in GenerationTarget.entries) {
            val cb = JBCheckBox(targetLabels[target] ?: target.displayName, target in selectedTargets)
            cb.addActionListener {
                selectedTargets = if (cb.isSelected) selectedTargets + target
                else selectedTargets - target
            }
            cb.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            targetPanel.add(cb)
        }

        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val selectAllBtn = JButton(Messages.get("select_all"))
        val deselectAllBtn = JButton(Messages.get("deselect_all"))

        selectAllBtn.addActionListener {
            targetPanel.components.filterIsInstance<JBCheckBox>().forEach { it.isSelected = true }
            selectedTargets = GenerationTarget.entries.toSet()
        }
        deselectAllBtn.addActionListener {
            targetPanel.components.filterIsInstance<JBCheckBox>().forEach { it.isSelected = false }
            selectedTargets = emptySet()
        }

        btnPanel.add(selectAllBtn)
        btnPanel.add(deselectAllBtn)
        btnPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        targetPanel.add(btnPanel)

        targetPanel.revalidate()
        targetPanel.repaint()
    }

    private fun onGenerateClicked() {
        val selectedTables = getSelectedTables()
        if (selectedTables.isEmpty()) {
            UIMessages.showInfoMessage(project, Messages.get("msg_select_tables"), Messages.get("dialog_title_info"))
            return
        }

        if (selectedTargets.isEmpty()) {
            UIMessages.showInfoMessage(project, Messages.get("msg_select_targets"), Messages.get("dialog_title_info"))
            return
        }

        if (GenerationTarget.SYNC_DB in selectedTargets) {
            runDatabaseSync(selectedTables)
        }

        val codeTargets = selectedTargets - GenerationTarget.SYNC_DB
        if (codeTargets.isNotEmpty()) {
            generateCodeFiles(selectedTables, codeTargets)
        }
    }

    private fun getSelectedTables(): List<TableInfo> {
        val checkboxes = tableCheckPanel.components.filterIsInstance<JBCheckBox>()
        return tables.filter { table ->
            checkboxes.find { it.text.contains(table.name) }?.isSelected == true
        }
    }

    private fun generateCodeFiles(tables: List<TableInfo>, targets: Set<GenerationTarget>) {
        val generator = CodeGenerator()
        val context = CodeGenerator.detectProjectContext(basePath)

        // Override framework based on user selection
        val selectedFrameworkIndex = frameworkCombo.selectedIndex
        val selectedFramework = when (selectedFrameworkIndex) {
            1 -> CodeGenerator.WebFramework.FASTAPI
            2 -> CodeGenerator.WebFramework.FLASK
            3 -> CodeGenerator.WebFramework.DJANGO_REST
            4 -> CodeGenerator.WebFramework.SANIC
            5 -> CodeGenerator.WebFramework.STARLETTE
            else -> context.framework
        }

        generator.setContext(context.copy(framework = selectedFramework))
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

                val fileList = generatedFiles.joinToString("\n") { "  $it" }
                val msg = Messages.get("msg_generated").format(tables.size, generatedFiles.size, fileList)
                UIMessages.showInfoMessage(project, msg, Messages.get("dialog_title_done"))
                statusLabel.text = Messages.get("status_done")
            } catch (ex: Exception) {
                UIMessages.showErrorDialog(project, Messages.get("msg_generate_failed").format(ex.message ?: ""), Messages.get("dialog_title_error"))
            }
        }
    }

    private fun runDatabaseSync(selectedTables: List<TableInfo>) {
        val configDialog = DatabaseConfigDialog(basePath)
        if (!configDialog.showAndGet()) return

        val connector = configDialog.buildConnector()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, Messages.get("task_sync_db"), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val syncer = DatabaseSyncer(connector)
                val result = try {
                    syncer.syncSelected(selectedTables)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        UIMessages.showErrorDialog(project, Messages.get("msg_sync_failed").format(ex.message ?: ""), Messages.get("dialog_title_error"))
                    }
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    val msg = buildString {
                        if (result.createdTables.isNotEmpty()) {
                            appendLine(Messages.get("msg_sync_created").format(result.createdTables.joinToString("\n") { "  $it" }))
                        }
                        if (result.alteredTables.isNotEmpty()) {
                            appendLine(Messages.get("msg_sync_altered").format(result.alteredTables.joinToString("\n") { "  $it" }))
                        }
                        if (result.createdTables.isEmpty() && result.alteredTables.isEmpty()) {
                            appendLine(Messages.get("msg_sync_uptodate"))
                        }
                        if (result.errors.isNotEmpty()) {
                            appendLine()
                            appendLine(Messages.get("msg_sync_warnings").format(result.errors.joinToString("\n") { "  $it" }))
                        }
                    }
                    UIMessages.showInfoMessage(project, msg.trim(), Messages.get("dialog_title_sync_done"))
                    statusLabel.text = Messages.get("status_done")
                }
            }
        })
    }

    private fun writeFile(basePath: String, dir: String, fileName: String, content: String) {
        val dirPath = File(basePath, dir)
        if (!dirPath.exists()) dirPath.mkdirs()
        File(dirPath, fileName).writeText(content)
    }

    private fun ensureInitFiles(basePath: String, dir: String) {
        val initFile = File(File(basePath, dir), "__init__.py")
        if (!initFile.exists()) initFile.writeText("")
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
}
