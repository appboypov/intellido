package dev.appboypov.intellido.todos.shared.models

/**
 * One `- [ ]` line of a list file.
 *
 * [line] is the zero-based line in the list file; [file] is the `[[file name]]` the todo is about,
 * [sourceLine] the one-based line it was captured from.
 */
data class Todo(
    val line: Int,
    val done: Boolean,
    val file: String?,
    val sourceLine: Int?,
    val text: String,
)
