package dev.appboypov.intellido.todos.shared.models

/** What one cleanup run changed. */
data class CleanupResult(val removedTodos: Int, val stampedTodos: Int, val deletedLists: List<String>)
