package dev.appboypov.intellido.todos.shared.services

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** When cleanup last ran in this project, kept in the workspace file so it stays out of version control. */
@Service(Service.Level.PROJECT)
@State(name = "IntelliDoCleanup", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TodoCleanupState : SimplePersistentStateComponent<TodoCleanupState.Values>(Values()) {
    class Values : BaseState() {
        var lastRunMillis by property(0L)
    }

    var lastRunMillis: Long
        get() = state.lastRunMillis
        set(value) {
            state.lastRunMillis = value
        }

    companion object {
        fun getInstance(project: Project): TodoCleanupState = project.service()
    }
}
