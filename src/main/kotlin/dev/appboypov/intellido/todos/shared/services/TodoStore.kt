package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException
import dev.appboypov.intellido.todos.shared.models.CleanupResult
import dev.appboypov.intellido.todos.shared.models.TodoList
import dev.appboypov.intellido.todos.shared.models.TodoTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Reads and changes the list files under [root], the todos folder (design D2). Knows nothing of the IDE.
 * [projectName] names the project root folder's list.
 */
class TodoStore(val root: Path, private val projectName: String) {
    /** Every list file under [root], ordered by path. */
    fun lists(): List<TodoList> {
        if (!root.isDirectory()) return emptyList()
        return Files.walk(root).use { paths ->
            paths.filter { it.isRegularFile() && it.name.endsWith(TodoList.MARKDOWN_EXTENSION) }.toList()
        }.map { read(relative(it)) }.sortedBy { it.relativePath.lowercase() }
    }

    /** The list at [relativePath], a path from [root] such as `src/auth.md`. */
    fun read(relativePath: String): TodoList {
        val lines = lines(pathOf(relativePath))
        return TodoList(relativePath, TodoMarkdown.folder(lines), TodoMarkdown.todos(lines))
    }

    /** The list file path, relative to [root], of the list that holds [folder]'s todos. */
    fun folderListPath(folder: String): String =
        (if (folder == TodoList.ROOT_FOLDER) projectName else folder) + TodoList.MARKDOWN_EXTENSION

    /** Adds [text] to the list of [target], creating the list with its frontmatter. Returns the list path. */
    fun add(target: TodoTarget, text: String, sourceLine: Int? = null): String {
        val relativePath = folderListPath(target.folder)
        val path = pathOf(relativePath)
        val line = TodoMarkdown.line(requireText(text), target.file, sourceLine)
        if (!path.exists()) {
            write(path, TodoMarkdown.frontmatter(target.folder) + line)
            return relativePath
        }
        val lines = lines(path)
        val owner = TodoMarkdown.folder(lines)
        if (owner != target.folder) {
            throw TodoActionException("$relativePath belongs to ${owner?.let { "folder $it" } ?: "a standalone list"}, not folder ${target.folder}")
        }
        write(path, appended(lines, line))
        return relativePath
    }

    /** Adds [text] to the existing list at [relativePath]. */
    fun addToList(relativePath: String, text: String) {
        val path = pathOf(relativePath)
        if (!path.exists()) throw TodoActionException("The list $relativePath does not exist")
        write(path, appended(lines(path), TodoMarkdown.line(requireText(text))))
    }

    /** Creates the empty standalone list [name]. Returns its path relative to [root]. */
    fun createList(name: String): String {
        val trimmed = name.trim().removeSuffix(TodoList.MARKDOWN_EXTENSION)
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\') || trimmed == "." || trimmed == "..") {
            throw TodoActionException("A list name cannot be empty or hold / or \\")
        }
        val relativePath = trimmed + TodoList.MARKDOWN_EXTENSION
        val path = pathOf(relativePath)
        if (path.exists()) throw TodoActionException("The list file $relativePath already exists")
        write(path, emptyList())
        return relativePath
    }

    /** Completes the open todo, or reopens the completed one, on [line] of [relativePath]. Returns whether it is done now. */
    fun toggle(relativePath: String, line: Int): Boolean {
        val path = pathOf(relativePath)
        val lines = lines(path).toMutableList()
        val todo = lines.getOrNull(line)?.let { TodoMarkdown.todo(it, line) }
            ?: throw TodoActionException("Line ${line + 1} of $relativePath is not a todo; the list changed")
        lines[line] = TodoMarkdown.checked(lines[line], !todo.done)
        write(path, lines)
        return !todo.done
    }

    /** Replaces the text of the todo on [line] of [relativePath] with [text], keeping its box and file link. */
    fun edit(relativePath: String, line: Int, text: String) {
        val path = pathOf(relativePath)
        val lines = lines(path).toMutableList()
        lines.getOrNull(line)?.let { TodoMarkdown.todo(it, line) }
            ?: throw TodoActionException("Line ${line + 1} of $relativePath is not a todo; the list changed")
        lines[line] = TodoMarkdown.withText(lines[line], requireText(text))
        write(path, lines)
    }

    /** Removes every completed todo, deletes list files left without todos and empty folders under [root]. */
    fun cleanup(): CleanupResult {
        var removed = 0
        val deleted = mutableListOf<String>()
        for (list in lists()) {
            val path = pathOf(list.relativePath)
            val lines = lines(path)
            val kept = lines.filterIndexed { index, line -> TodoMarkdown.todo(line, index)?.done != true }
            removed += lines.size - kept.size
            if (TodoMarkdown.todos(kept).isEmpty()) {
                path.deleteIfExists()
                deleted += list.relativePath
            } else if (kept != lines) {
                write(path, kept)
            }
        }
        deleteEmptyFolders(root)
        return CleanupResult(removed, deleted)
    }

    /** The absolute path of the list at [relativePath]. Rejects paths that leave [root]. */
    fun pathOf(relativePath: String): Path {
        val path = root.resolve(relativePath).normalize()
        if (!path.startsWith(root.normalize())) throw TodoActionException("$relativePath is outside the todos folder")
        return path
    }

    private fun relative(path: Path) = root.relativize(path).invariantSeparatorsPathString

    private fun requireText(text: String) = text.trim().ifEmpty { throw TodoActionException("A todo needs text") }

    private fun lines(path: Path): List<String> = if (path.exists()) path.readText().lines().dropLastWhile { it.isEmpty() } else emptyList()

    private fun appended(lines: List<String>, line: String) = lines.dropLastWhile { it.isBlank() } + line

    private fun write(path: Path, lines: List<String>) {
        path.parent.createDirectories()
        path.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))
    }

    private fun deleteEmptyFolders(folder: Path) {
        if (!folder.isDirectory()) return
        folder.listDirectoryEntries().filter { it.isDirectory() }.forEach { child ->
            deleteEmptyFolders(child)
            if (child.listDirectoryEntries().isEmpty()) child.deleteIfExists()
        }
    }
}
