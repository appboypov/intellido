package dev.appboypov.intellido.todos.shared.views.todopanel

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import dev.appboypov.intellido.core.services.IntelliDoBundle
import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException

/**
 * The IDE route to a panel interaction (design D6). Registered once per action id in plugin.xml;
 * it runs the [TodoPanelViewService] handler registered under its own id.
 */
class TodoPanelAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val id = ActionManager.getInstance().getId(this) ?: return
        try {
            TodoPanelViewService.getInstance(project).run(id)
        } catch (failure: TodoActionException) {
            Messages.showErrorDialog(project, failure.message, IntelliDoBundle.message("error.title"))
        }
    }
}
