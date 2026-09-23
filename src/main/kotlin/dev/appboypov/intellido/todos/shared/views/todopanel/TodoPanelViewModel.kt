package dev.appboypov.intellido.todos.shared.views.todopanel

import dev.appboypov.intellido.todos.shared.models.TodoList
import dev.appboypov.intellido.todos.shared.services.TodoRepository
import kotlinx.coroutines.flow.StateFlow

/** The IntelliDo panel's state, and the intents it forwards to [TodoPanelViewService] by action name. */
class TodoPanelViewModel(private val service: TodoPanelViewService, repository: TodoRepository) {
    val lists: StateFlow<List<TodoList>> = repository.lists

    /** Adds [text] to the list at [list], or to the project root folder's list when null. */
    fun add(text: String, list: String?) =
        service.run(TodoPanelViewService.ADD, if (list != null) mapOf("list" to list, "text" to text) else mapOf("path" to ".", "text" to text))

    fun toggle(list: String, line: Int) = service.run(TodoPanelViewService.TOGGLE, mapOf("list" to list, "line" to line.toString()))

    fun open(list: String, line: Int) = service.run(TodoPanelViewService.OPEN, mapOf("list" to list, "line" to line.toString()))

    fun refresh() = service.run(TodoPanelViewService.REFRESH)

    fun attach(view: TodoPanelView) = service.attach(view)

    fun detach(view: TodoPanelView) = service.detach(view)
}
