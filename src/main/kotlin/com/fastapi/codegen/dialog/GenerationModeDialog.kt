package com.fastapi.codegen.dialog

import com.fastapi.codegen.i18n.Messages
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages as UIMessages
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Dimension

class GenerationModeDialog : DialogWrapper(true) {

    var useDatabase: Boolean = false
        private set

    init {
        title = Messages.get("mode_label")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(400, 150)

        val choice = UIMessages.showYesNoDialog(
            Messages.get("mode_from_database") + "?",
            Messages.get("mode_label"),
            Messages.get("mode_from_database"),
            Messages.get("mode_from_models"),
            UIMessages.getQuestionIcon()
        )

        useDatabase = (choice == UIMessages.YES)
        return panel
    }

    override fun doOKAction() {
        super.doOKAction()
    }
}
