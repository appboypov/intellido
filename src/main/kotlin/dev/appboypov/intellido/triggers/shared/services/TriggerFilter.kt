package dev.appboypov.intellido.triggers.shared.services

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Decides from a project-relative path (with `/` separators) whether trigger capture scans a file (design D4).
 * [todosFolder] is the todos folder relative to the project, or null when it is outside the project.
 * [ignore] and [whitelist] entries are paths, covering everything under them, or globs when they hold `*`, `?` or `[`.
 */
class TriggerFilter(todosFolder: String?, ignore: List<String>, whitelist: List<String>) {
    private val todos = todosFolder?.let(::normalize)?.let(::Entry)
    private val ignored = ignore.mapNotNull(::entryOf)
    private val whitelisted = whitelist.mapNotNull(::entryOf)

    fun accepts(relativePath: String): Boolean {
        val path = normalize(relativePath)
        if (path.isEmpty() || path.split('/').contains(GIT_FOLDER)) return false
        if (todos != null && todos.covers(path)) return false
        if (ignored.any { it.covers(path) }) return false
        return whitelisted.isEmpty() || whitelisted.any { it.covers(path) }
    }

    private class Entry(val path: String, val glob: PathMatcher? = null) {
        /** Whether [candidate] is this entry or lies under it. */
        fun covers(candidate: String): Boolean {
            if (glob == null) return candidate == path || candidate.startsWith("$path/")
            var current: String? = candidate
            while (current != null) {
                if (glob.matches(Path.of(current))) return true
                current = current.substringBeforeLast('/', "").ifEmpty { null }
            }
            return false
        }
    }

    private fun entryOf(raw: String): Entry? {
        val entry = normalize(raw)
        if (entry.isEmpty()) return null
        val isGlob = entry.any { it == '*' || it == '?' || it == '[' }
        return Entry(entry, if (isGlob) FileSystems.getDefault().getPathMatcher("glob:$entry") else null)
    }

    private fun normalize(raw: String): String = raw.trim().replace('\\', '/').removePrefix("./").trim('/')

    private companion object {
        const val GIT_FOLDER = ".git"
    }
}
