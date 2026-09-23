package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.models.TodoList
import dev.appboypov.intellido.todos.shared.models.TodoTarget
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

/** Maps project files and folders to the list their todos go to. */
object TodoTargets {
    /**
     * The target for [path] in the project at [projectRoot]: a folder is its own target, a file belongs to its folder.
     * Null when [path] is outside the project.
     */
    fun of(projectRoot: Path, path: Path, isDirectory: Boolean): TodoTarget? {
        val root = projectRoot.normalize()
        val target = path.normalize()
        if (!target.startsWith(root)) return null
        return if (isDirectory) {
            TodoTarget(folderOf(root, target), null)
        } else {
            TodoTarget(folderOf(root, target.parent ?: return null), target.name)
        }
    }

    /** The project file or folder a todo in [folder] about [file] points at. */
    fun resolve(projectRoot: Path, folder: String, file: String?): Path {
        val folderPath = if (folder == TodoList.ROOT_FOLDER) projectRoot else projectRoot.resolve(folder)
        return if (file == null) folderPath else folderPath.resolve(file)
    }

    private fun folderOf(root: Path, folder: Path): String =
        root.relativize(folder).invariantSeparatorsPathString.ifEmpty { TodoList.ROOT_FOLDER }
}
