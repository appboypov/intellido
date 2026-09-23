package dev.appboypov.intellido.todos.shared.models

/**
 * One list file under the todos folder.
 *
 * [relativePath] is the file's path from the todos folder with `/` separators, such as `src/auth.md`.
 * [folder] is the project folder the list belongs to (`.` for the project root), or null for a standalone list.
 */
data class TodoList(
    val relativePath: String,
    val folder: String?,
    val todos: List<Todo>,
) {
    /** The folder path for a folder list, else the file name without `.md`. */
    val title: String
        get() = folder?.takeIf { it != ROOT_FOLDER } ?: relativePath.removeSuffix(MARKDOWN_EXTENSION)

    companion object {
        const val ROOT_FOLDER = "."
        const val MARKDOWN_EXTENSION = ".md"
    }
}
