package dev.appboypov.intellido.triggers.shared.models

/** The configured trigger markers (ADR-0002). Each marker counts only when its switch is on. */
data class TriggerPattern(
    val start: String,
    val useStart: Boolean,
    val contains: String,
    val useContains: Boolean,
    val end: String,
    val useEnd: Boolean,
) {
    /** At least one switched-on marker with text; otherwise nothing is ever captured. */
    val isUsable: Boolean
        get() = (useStart && start.isNotEmpty()) || (useContains && contains.isNotEmpty()) || (useEnd && end.isNotEmpty())
}
