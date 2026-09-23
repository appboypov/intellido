package dev.appboypov.intellido.todos.shared.views.todopanel

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.appboypov.intellido.todos.shared.services.TodoRepository

/** Registers the docked `IntelliDo` panel (spec: the IntelliDo panel docks in the IDE). */
class TodoToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(null, "", false)
        val viewModel = TodoPanelViewModel(TodoPanelViewService.getInstance(project), TodoRepository.getInstance(project))
        val view = TodoPanelView(project, viewModel, content)
        content.component = view.component
        content.preferredFocusableComponent = view.focusTarget
        toolWindow.contentManager.addContent(content)
        val actions = ActionManager.getInstance()
        toolWindow.setTitleActions(listOfNotNull(actions.getAction(HEADER_GROUP)))
        (actions.getAction(GEAR_GROUP) as? ActionGroup)?.let(toolWindow::setAdditionalGearActions)
    }

    companion object {
        const val ID = "IntelliDo"
        private const val HEADER_GROUP = "intellido.panel.header"
        private const val GEAR_GROUP = "intellido.panel.gear"
    }
}
