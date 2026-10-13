package com.fastapi.codegen.dialog

import com.fastapi.codegen.i18n.Messages
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class GenerateOptionsDialog : DialogWrapper(true) {

    enum class GenerationTarget(val key: String) {
        MODELS("target_models"),
        SCHEMAS("target_schemas"),
        SERVICES("target_services"),
        ROUTERS("target_routers"),
        SYNC_DB("target_sync_db");

        val displayName: String get() = Messages.get(key)
    }

    var selectedTargets: Set<GenerationTarget> = setOf(
        GenerationTarget.SCHEMAS,
        GenerationTarget.SERVICES,
        GenerationTarget.ROUTERS
    )
        private set

    init {
        title = Messages.get("dialog_title_info")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label(Messages.get("targets_header")).bold()
            }
            row {
                checkBox(GenerationTarget.MODELS.displayName)
                    .bindSelected({ GenerationTarget.MODELS in selectedTargets }, {
                        selectedTargets = if (it) selectedTargets + GenerationTarget.MODELS
                        else selectedTargets - GenerationTarget.MODELS
                    })
            }
            row {
                checkBox(GenerationTarget.SCHEMAS.displayName)
                    .bindSelected({ GenerationTarget.SCHEMAS in selectedTargets }, {
                        selectedTargets = if (it) selectedTargets + GenerationTarget.SCHEMAS
                        else selectedTargets - GenerationTarget.SCHEMAS
                    })
            }
            row {
                checkBox(GenerationTarget.SERVICES.displayName)
                    .bindSelected({ GenerationTarget.SERVICES in selectedTargets }, {
                        selectedTargets = if (it) selectedTargets + GenerationTarget.SERVICES
                        else selectedTargets - GenerationTarget.SERVICES
                    })
            }
            row {
                checkBox(GenerationTarget.ROUTERS.displayName)
                    .bindSelected({ GenerationTarget.ROUTERS in selectedTargets }, {
                        selectedTargets = if (it) selectedTargets + GenerationTarget.ROUTERS
                        else selectedTargets - GenerationTarget.ROUTERS
                    })
            }
            row {
                checkBox(GenerationTarget.SYNC_DB.displayName)
                    .bindSelected({ GenerationTarget.SYNC_DB in selectedTargets }, {
                        selectedTargets = if (it) selectedTargets + GenerationTarget.SYNC_DB
                        else selectedTargets - GenerationTarget.SYNC_DB
                    })
            }
            row {
                button(Messages.get("select_all")) {
                    selectedTargets = GenerationTarget.entries.toSet()
                }
                button(Messages.get("deselect_all")) {
                    selectedTargets = emptySet()
                }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedTargets.isEmpty()) {
            return ValidationInfo(Messages.get("msg_select_targets"))
        }
        return null
    }
}
