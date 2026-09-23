package dev.appboypov.intellido.triggers.shared.services

import dev.appboypov.intellido.triggers.shared.models.TriggerCapture
import dev.appboypov.intellido.triggers.shared.models.TriggerPattern

/** Finds and cuts triggers (ADR-0002). Pure. */
object TriggerMatcher {
    private val LIST_MARKER_ONLY = Regex("""^\s*(?:[-*+]|\d+[.)])(?:\s+\[[ xX]\])?\s*$""")

    /** Every trigger in [text], one per line at most. */
    fun scan(text: String, pattern: TriggerPattern): List<TriggerCapture> {
        if (!pattern.isUsable) return emptyList()
        return text.split('\n').mapIndexedNotNull { index, line -> match(line, index, pattern) }
    }

    /** [text] with every capture applied: remainders replace their lines, deleted lines are gone. */
    fun apply(text: String, captures: List<TriggerCapture>): String {
        val byLine = captures.associateBy { it.line }
        return text.split('\n').withIndex().mapNotNull { (index, line) ->
            val capture = byLine[index] ?: return@mapNotNull line
            capture.remainder
        }.joinToString("\n")
    }

    /** The trigger on [line], zero-based line [index], or null. */
    fun match(line: String, index: Int, pattern: TriggerPattern): TriggerCapture? {
        if (!pattern.isUsable) return null
        val carriageReturn = line.endsWith('\r')
        val body = line.removeSuffix("\r")
        val start = pattern.start.takeIf { pattern.useStart && it.isNotEmpty() }
        val contains = pattern.contains.takeIf { pattern.useContains && it.isNotEmpty() }
        val end = pattern.end.takeIf { pattern.useEnd && it.isNotEmpty() }

        // Where the trigger begins, and the text segments between its opening markers.
        val begin: Int
        val openEnd: Int
        var leading = ""
        if (contains != null) {
            val containsAt = body.indexOf(contains)
            if (containsAt < 0) return null
            if (start != null) {
                val startAt = if (containsAt >= start.length) body.lastIndexOf(start, containsAt - start.length) else -1
                if (startAt < 0) return null
                begin = startAt
                leading = body.substring(startAt + start.length, containsAt)
            } else {
                begin = containsAt
            }
            openEnd = containsAt + contains.length
        } else if (start != null) {
            begin = body.indexOf(start)
            if (begin < 0) return null
            openEnd = begin + start.length
        } else {
            begin = body.indexOfFirst { !it.isWhitespace() }
            if (begin < 0) return null
            openEnd = begin
        }

        val trimmed = body.trimEnd()
        val closeStart = if (end != null) {
            if (!trimmed.endsWith(end)) return null
            (trimmed.length - end.length).also { if (it < openEnd) return null }
        } else {
            trimmed.length
        }
        if (closeStart < openEnd) return null

        val text = listOf(leading, body.substring(openEnd, closeStart))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (text.isEmpty()) return null

        val before = body.substring(0, begin).trimEnd()
        val remainder = if (before.isBlank() || LIST_MARKER_ONLY.matches(before)) null else before + if (carriageReturn) "\r" else ""
        return TriggerCapture(index, text, remainder)
    }
}
