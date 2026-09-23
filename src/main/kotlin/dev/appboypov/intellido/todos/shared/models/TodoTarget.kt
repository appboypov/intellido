package dev.appboypov.intellido.todos.shared.models

/** Where a todo goes: the list of [folder] (`.` for the project root), about [file] in that folder or the folder itself. */
data class TodoTarget(val folder: String, val file: String?)
