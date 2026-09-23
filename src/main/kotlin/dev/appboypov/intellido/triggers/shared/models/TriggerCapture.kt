package dev.appboypov.intellido.triggers.shared.models

/**
 * One trigger found on zero-based [line]: the todo [text], and the [remainder] of the line after the
 * trigger is cut, or null when the line is to be deleted.
 */
data class TriggerCapture(val line: Int, val text: String, val remainder: String?)
