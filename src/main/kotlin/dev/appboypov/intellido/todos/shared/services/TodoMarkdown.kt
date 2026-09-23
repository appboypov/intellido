package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.models.Todo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The list file format (ADR-0001): frontmatter `folder`, one todo per `- [ ]` line,
 * `[[file name]]` or `[[file name]]:line` before the text, ` ✅ YYYY-MM-DDTHH:MM` after a completed one.
 */
object TodoMarkdown {
    private val TODO_LINE = Regex("""^(\s*[-*+] \[)([ xX])\](?: (.*))?$""")
    private val FILE_LINK = Regex("""^\[\[([^\]]+)\]\](?::(\d+))?(?: (.*))?$""")
    private val STAMP = Regex("""\s*$STAMP_MARK (\d{4}-\d{2}-\d{2}T\d{2}:\d{2})\s*$""")
    private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private const val FENCE = "---"
    private const val FOLDER_KEY = "folder"

    const val STAMP_MARK = "✅"

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
        val done = match.groupValues[2] != " "
        var rest = match.groups[3]?.value.orEmpty()
        val stamp = STAMP.find(rest)
        val completedAt = stamp?.let { parseStamp(it.groupValues[1]) }
        if (stamp != null && completedAt != null) rest = rest.substring(0, stamp.range.first)
        val link = FILE_LINK.matchEntire(rest)
        return Todo(
            line = index,
            done = done,
            file = link?.groupValues?.get(1),
            sourceLine = link?.groups?.get(2)?.value?.toIntOrNull(),
            text = (if (link != null) link.groups[3]?.value.orEmpty() else rest).trim(),
            completedAt = completedAt,
        )
    }

    /** A new open todo line. */
    fun line(text: String, file: String? = null, sourceLine: Int? = null): String {
        val link = file?.let { "[[$it]]" + (sourceLine?.let { line -> ":$line" } ?: "") + " " } ?: ""
        return "- [ ] $link${text.trim()}"
    }

    /** [line] completed at [at]: the box ticked and the stamp replaced. */
    fun complete(line: String, at: LocalDateTime): String {
        val match = TODO_LINE.matchEntire(line) ?: return line
        val rest = match.groups[3]?.value.orEmpty().let { STAMP.replace(it, "") }.trimEnd()
        return "${match.groupValues[1]}x] $rest $STAMP_MARK ${at.format(STAMP_FORMAT)}"
    }

    /** [line] reopened: the box cleared and the stamp removed. */
    fun reopen(line: String): String {
        val match = TODO_LINE.matchEntire(line) ?: return line
        val rest = match.groups[3]?.value.orEmpty().let { STAMP.replace(it, "") }.trimEnd()
        return "${match.groupValues[1]} ] $rest".trimEnd()
    }

    /** The frontmatter that names [folder]. */
    fun frontmatter(folder: String): List<String> = listOf(FENCE, "$FOLDER_KEY: $folder", FENCE, "")

    private fun parseStamp(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value, STAMP_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }
}
