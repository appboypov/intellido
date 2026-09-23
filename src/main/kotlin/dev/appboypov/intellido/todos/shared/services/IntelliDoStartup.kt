package dev.appboypov.intellido.todos.shared.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.appboypov.intellido.triggers.shared.services.TriggerCaptureService

/** On project open: reads the lists, starts scheduled cleanup and trigger detection. */
class IntelliDoStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        TodoRepository.getInstance(project).start()
        TriggerCaptureService.getInstance(project).start()
    }
}
