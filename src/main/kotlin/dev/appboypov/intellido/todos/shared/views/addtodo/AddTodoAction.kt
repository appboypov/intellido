package dev.appboypov.intellido.todos.shared.views.addtodo

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import dev.appboypov.intellido.core.services.IntelliDoBundle
import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException
import dev.appboypov.intellido.todos.shared.services.TodoRepository
import dev.appboypov.intellido.todos.shared.views.todopanel.TodoPanelViewService
import kotlin.io.path.invariantSeparatorsPathString

/**
 * `Add Todo…` for the selected files and folders in the project view, or the edited file in an editor.
 * Listed in the Keymap, so the user can bind a shortcut. Runs [TodoPanelViewService.ADD].
 */
class AddTodoAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && selection(e).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = TodoRepository.getInstance(project).projectRoot
        val paths = selection(e).map { it.toNioPath() }.filter { it.startsWith(root) }
            .joinToString("\n") { root.relativize(it).invariantSeparatorsPathString.ifEmpty { "." } }
        try {
            TodoPanelViewService.getInstance(project).run(TodoPanelViewService.ADD, mapOf("path" to paths))
        } catch (failure: TodoActionException) {
            Messages.showErrorDialog(project, failure.message, IntelliDoBundle.message("error.title"))
        }
    }

    private fun selection(e: AnActionEvent): List<VirtualFile> =
        (e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: listOfNotNull(e.getData(CommonDataKeys.VIRTUAL_FILE)))
            .filter { it.isInLocalFileSystem }
}
