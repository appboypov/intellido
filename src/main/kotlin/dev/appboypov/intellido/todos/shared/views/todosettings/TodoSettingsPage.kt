package dev.appboypov.intellido.todos.shared.views.todosettings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import dev.appboypov.intellido.core.services.IntelliDoBundle
import dev.appboypov.intellido.todos.shared.services.IntelliDoSettings
import dev.appboypov.intellido.todos.shared.services.TodoRepository
import dev.appboypov.intellido.triggers.shared.enums.TriggerDetection
import dev.appboypov.intellido.triggers.shared.services.TriggerCaptureService
import javax.swing.JCheckBox

/** Settings | Tools | IntelliDo: the project's todos folder, cleanup, trigger pattern, detection and filters (design D7). */
class TodoSettingsPage(private val project: Project) : BoundConfigurable(IntelliDoBundle.message("settings.displayName")) {
    private val settings = IntelliDoSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group(IntelliDoBundle.message("settings.group.lists")) {
            row(IntelliDoBundle.message("settings.todosFolder.label")) {
                textField().bindText(settings::todosFolder).align(AlignX.FILL)
                    .comment(IntelliDoBundle.message("settings.todosFolder.comment"))
            }
            row(IntelliDoBundle.message("settings.cleanupHours.label")) {
                intTextField(1..24 * 365).bindIntText(settings::cleanupHours)
                    .comment(IntelliDoBundle.message("settings.cleanupHours.comment"))
            }
        }
        group(IntelliDoBundle.message("settings.group.pattern")) {
            lateinit var start: Cell<JCheckBox>
            lateinit var contains: Cell<JCheckBox>
            lateinit var end: Cell<JCheckBox>
            row {
                start = checkBox(IntelliDoBundle.message("settings.start.label")).bindSelected(settings::useStart)
                textField().bindText(settings::startMarker).enabledIf(start.selected)
            }
            row {
                contains = checkBox(IntelliDoBundle.message("settings.contains.label")).bindSelected(settings::useContains)
                textField().bindText(settings::containsMarker).enabledIf(contains.selected)
            }
            row {
                end = checkBox(IntelliDoBundle.message("settings.end.label")).bindSelected(settings::useEnd)
                textField().bindText(settings::endMarker).enabledIf(end.selected)
            }.rowComment(IntelliDoBundle.message("settings.pattern.comment"))
            listOf(start, contains, end).forEach { box ->
                box.validationOnApply {
                    if (listOf(start, contains, end).none { it.component.isSelected }) error(IntelliDoBundle.message("settings.pattern.none")) else null
                }
            }
        }
        group(IntelliDoBundle.message("settings.group.detection")) {
            lateinit var poll: Cell<*>
            buttonsGroup {
                row { radioButton(IntelliDoBundle.message("settings.detection.watch"), TriggerDetection.WATCH) }
                row {
                    val pollButton = radioButton(IntelliDoBundle.message("settings.detection.poll"), TriggerDetection.POLL)
                    poll = intTextField(IntelliDoSettings.MIN_POLL_SECONDS..86_400).bindIntText(settings::pollSeconds).enabledIf(pollButton.selected)
                    label(IntelliDoBundle.message("settings.detection.seconds"))
                }
            }.bind(settings::detection)
        }
        group(IntelliDoBundle.message("settings.group.filters")) {
            row { checkBox(IntelliDoBundle.message("settings.skipGitIgnored.label")).bindSelected(settings::skipGitIgnored) }
            row(IntelliDoBundle.message("settings.ignore.label")) {}
            row {
                textArea().rows(4).align(AlignX.FILL)
                    .bindText({ settings.ignore.joinToString("\n") }, { settings.ignore = it.lines() })
                    .comment(IntelliDoBundle.message("settings.paths.comment"))
            }
            row(IntelliDoBundle.message("settings.whitelist.label")) {}
            row {
                textArea().rows(4).align(AlignX.FILL)
                    .bindText({ settings.whitelist.joinToString("\n") }, { settings.whitelist = it.lines() })
                    .comment(IntelliDoBundle.message("settings.whitelist.comment"))
            }
        }
    }

    override fun apply() {
        super.apply()
        TodoRepository.getInstance(project).reload()
        TriggerCaptureService.getInstance(project).start()
    }
}
