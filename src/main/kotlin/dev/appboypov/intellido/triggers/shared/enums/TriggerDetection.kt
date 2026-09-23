package dev.appboypov.intellido.triggers.shared.enums

/** How trigger capture notices changed files (design D4). */
enum class TriggerDetection {
    /** React to files written to disk, as a file watcher does. */
    WATCH,

    /** Rescan the project on a fixed interval. */
    POLL,
}
