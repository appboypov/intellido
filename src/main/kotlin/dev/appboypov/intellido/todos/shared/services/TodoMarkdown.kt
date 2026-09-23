package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.models.Todo

/**
 * The list file format (ADR-0001): frontmatter `folder`, one todo per `- [ ]` line,
 * `[[file name]]` or `[[file name]]:line` before the text.
 */
object TodoMarkdown {
    private val TODO_LINE = Regex("""^(\s*[-*+] \[)([ xX])\](?: (.*))?$""")
    private val FILE_LINK = Regex("""^\[\[([^\]]+)\]\](?::(\d+))?(?: (.*))?$""")
    private const val FENCE = "---"
    private const val FOLDER_KEY = "folder"

    /** The `folder` value of the frontmatter at the top of [lines], or null. */
    fun folder(lines: List<String>): String? {
        if (lines.firstOrNull()?.trim() != FENCE) return null
        for (line in lines.drop(1)) {
            if (line.trim() == FENCE) return null
            val key = line.substringBefore(':', "").trim()
            if (key == FOLDER_KEY) return line.substringAfter(':').trim().removeSurrounding("\"").removeSurrounding("'").ifEmpty { null }
        }
        return null
    }

    /** Every todo in [lines], with its line index. */
    fun todos(lines: List<String>): List<Todo> = lines.mapIndexedNotNull { index, line -> todo(line, index) }

    /** The todo on [line], or null when it is not a todo line. */
    fun todo(line: String, index: Int): Todo? {
        val match = TODO_LINE.matchEntire(line) ?: return null
        val rest = match.groups[3]?.value.orEmpty()
        val link = FILE_LINK.matchEntire(rest)
        return Todo(
            line = index,
            done = match.groupValues[2] != " ",
            file = link?.groupValues?.get(1),
            sourceLine = link?.groups?.get(2)?.value?.toIntOrNull(),
            text = (if (link != null) link.groups[3]?.value.orEmpty() else rest).trim(),
        )
    }

    /** A new open todo line. */
    fun line(text: String, file: String? = null, sourceLine: Int? = null): String {
        val link = file?.let { "[[$it]]" + (sourceLine?.let { line -> ":$line" } ?: "") + " " } ?: ""
        return "- [ ] $link${text.trim()}"
    }

    /** [line] with its box ticked when [done], cleared otherwise; nothing else on the line changes. */
    fun checked(line: String, done: Boolean): String {
        val match = TODO_LINE.matchEntire(line) ?: return line
        return match.groupValues[1] + (if (done) "x" else " ") + line.substring(match.groups[2]!!.range.last + 1)
    }

    /** [line] with its text replaced by [text]; the box and the file link stay. */
    fun withText(line: String, text: String): String {
        val match = TODO_LINE.matchEntire(line) ?: return line
        val link = FILE_LINK.matchEntire(match.groups[3]?.value.orEmpty())
            ?.let { "[[${it.groupValues[1]}]]" + (it.groups[2]?.let { number -> ":${number.value}" } ?: "") + " " } ?: ""
        return "${match.groupValues[1]}${match.groupValues[2]}] $link${text.trim()}"
    }

    /** The frontmatter that names [folder]. */
    fun frontmatter(folder: String): List<String> = listOf(FENCE, "$FOLDER_KEY: $folder", FENCE, "")
}
