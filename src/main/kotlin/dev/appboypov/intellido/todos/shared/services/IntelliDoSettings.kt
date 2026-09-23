package dev.appboypov.intellido.todos.shared.services

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.appboypov.intellido.triggers.shared.enums.TriggerDetection
import dev.appboypov.intellido.triggers.shared.models.TriggerPattern
import java.time.Duration

/** The plugin's settings for one project, stored in `.idea/intellido.xml` (design D7). */
@Service(Service.Level.PROJECT)
@State(name = "IntelliDoSettings", storages = [Storage("intellido.xml")])
class IntelliDoSettings : SimplePersistentStateComponent<IntelliDoSettings.Values>(Values()) {
    class Values : BaseState() {
        var todosFolder by string(DEFAULT_TODOS_FOLDER)
        var cleanupHours by property(DEFAULT_CLEANUP_HOURS)
        var detection by enum(TriggerDetection.WATCH)
        var pollSeconds by property(DEFAULT_POLL_SECONDS)
        var startMarker by string(DEFAULT_START)
        var useStart by property(true)
        var containsMarker by string(DEFAULT_CONTAINS)
        var useContains by property(true)
        var endMarker by string(DEFAULT_END)
        var useEnd by property(true)
        var skipGitIgnored by property(true)
        var ignore by list<String>()
        var whitelist by list<String>()
    }

    /** The todos folder as typed: relative to the project root, or absolute. */
    var todosFolder: String
        get() = state.todosFolder?.trim()?.ifEmpty { null } ?: DEFAULT_TODOS_FOLDER
        set(value) {
            state.todosFolder = value.trim()
        }

    var cleanupHours: Int
        get() = state.cleanupHours.coerceAtLeast(1)
        set(value) {
            state.cleanupHours = value.coerceAtLeast(1)
        }

    val cleanupAge: Duration get() = Duration.ofHours(cleanupHours.toLong())

    var detection: TriggerDetection
        get() = state.detection
        set(value) {
            state.detection = value
        }

    var pollSeconds: Int
        get() = state.pollSeconds.coerceAtLeast(MIN_POLL_SECONDS)
        set(value) {
            state.pollSeconds = value.coerceAtLeast(MIN_POLL_SECONDS)
        }

    var startMarker: String
        get() = state.startMarker.orEmpty()
        set(value) {
            state.startMarker = value
        }

    var useStart: Boolean
        get() = state.useStart
        set(value) {
            state.useStart = value
        }

    var containsMarker: String
        get() = state.containsMarker.orEmpty()
        set(value) {
            state.containsMarker = value
        }

    var useContains: Boolean
        get() = state.useContains
        set(value) {
            state.useContains = value
        }

    var endMarker: String
        get() = state.endMarker.orEmpty()
        set(value) {
            state.endMarker = value
        }

    var useEnd: Boolean
        get() = state.useEnd
        set(value) {
            state.useEnd = value
        }

    var skipGitIgnored: Boolean
        get() = state.skipGitIgnored
        set(value) {
            state.skipGitIgnored = value
        }

    var ignore: List<String>
        get() = state.ignore.toList()
        set(value) {
            state.ignore = value.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        }

    var whitelist: List<String>
        get() = state.whitelist.toList()
        set(value) {
            state.whitelist = value.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        }

    val pattern: TriggerPattern
        get() = TriggerPattern(startMarker, useStart, containsMarker, useContains, endMarker, useEnd)

    companion object {
        const val DEFAULT_TODOS_FOLDER = "todos"
        const val DEFAULT_CLEANUP_HOURS = 24
        const val DEFAULT_POLL_SECONDS = 30
        const val MIN_POLL_SECONDS = 5
        const val DEFAULT_START = "//"
        const val DEFAULT_CONTAINS = "#todo"
        const val DEFAULT_END = ";"

        fun getInstance(project: Project): IntelliDoSettings = project.service()
    }
}
