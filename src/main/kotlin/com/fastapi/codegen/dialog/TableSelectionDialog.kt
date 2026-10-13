package com.fastapi.codegen.dialog

import com.fastapi.codegen.i18n.Messages
import com.fastapi.codegen.model.TableInfo
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*

class TableSelectionDialog(
    private val tables: List<TableInfo>,
    private val basePath: String? = null
) : DialogWrapper(true) {

    private val checkBoxes = mutableMapOf<String, JBCheckBox>()
    var selectedTables: List<TableInfo> = emptyList()
        private set

    init {
        title = Messages.get("dialog_title_info")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(500, 500)

        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.X_AXIS)

        val selectAllBtn = JButton(Messages.get("select_all"))
        selectAllBtn.addActionListener { checkBoxes.values.forEach { it.isSelected = true } }

        val deselectAllBtn = JButton(Messages.get("deselect_all"))
        deselectAllBtn.addActionListener { checkBoxes.values.forEach { it.isSelected = false } }

        topPanel.add(selectAllBtn)
        topPanel.add(deselectAllBtn)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        val checkPanel = JPanel()
        checkPanel.layout = BoxLayout(checkPanel, BoxLayout.Y_AXIS)

        val colSuffix = Messages.get("columns_suffix")

        for (table in tables) {
            val relativePath = table.sourceFile?.let {
                basePath?.let { bp -> File(bp, it).toRelativeString(File(bp)) } ?: it
            } ?: table.name

            val label = buildString {
                append("[$relativePath] ")
                append(table.name)
                table.comment?.let { append(" ($it)") }
                append(" [${table.columns.size} $colSuffix]")
            }
            val cb = JBCheckBox(label, true)
            checkBoxes[table.name] = cb
            checkPanel.add(cb)
        }

        mainPanel.add(JBScrollPane(checkPanel), BorderLayout.CENTER)
        return mainPanel
    }

    override fun doOKAction() {
        selectedTables = tables.filter { checkBoxes[it.name]?.isSelected == true }
        super.doOKAction()
    }
}
