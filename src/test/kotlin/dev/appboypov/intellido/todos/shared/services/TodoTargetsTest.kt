package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.models.TodoTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TodoTargetsTest {
    private val root = Path.of("/work/shop")

    @Test
    fun `files belong to their folder and folders to themselves`() {
        assertEquals(TodoTarget("src/auth", "Login.kt"), TodoTargets.of(root, root.resolve("src/auth/Login.kt"), isDirectory = false))
        assertEquals(TodoTarget("src/auth", null), TodoTargets.of(root, root.resolve("src/auth"), isDirectory = true))
        assertEquals(TodoTarget(".", "README.md"), TodoTargets.of(root, root.resolve("README.md"), isDirectory = false))
        assertEquals(TodoTarget(".", null), TodoTargets.of(root, root, isDirectory = true))
    }

    @Test
    fun `paths outside the project have no target`() {
        assertNull(TodoTargets.of(root, Path.of("/work/other/a.kt"), isDirectory = false))
    }
}
