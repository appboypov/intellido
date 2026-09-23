package dev.appboypov.intellido.triggers.shared.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TriggerFilterTest {
    @Test
    fun `skips the todos folder and git metadata`() {
        val filter = TriggerFilter("todos", emptyList(), emptyList())
        assertFalse(filter.accepts("todos/src/auth.md"))
        assertFalse(filter.accepts(".git/config"))
        assertFalse(filter.accepts("sub/.git/HEAD"))
        assertTrue(filter.accepts("todos.kt"))
        assertTrue(filter.accepts("src/Main.kt"))
    }

    @Test
    fun `an ignored path covers everything under it`() {
        val filter = TriggerFilter(null, listOf("docs/", "./notes.md"), emptyList())
        assertFalse(filter.accepts("docs/guide.md"))
        assertFalse(filter.accepts("notes.md"))
        assertTrue(filter.accepts("docsite/index.md"))
    }

    @Test
    fun `a glob matches the file or one of its folders`() {
        val filter = TriggerFilter(null, listOf("**/generated", "*.lock"), emptyList())
        assertFalse(filter.accepts("app/generated/Api.kt"))
        assertFalse(filter.accepts("bun.lock"))
        assertTrue(filter.accepts("app/src/Api.kt"))
    }

    @Test
    fun `a whitelist limits scanning and ignore wins over it`() {
        val filter = TriggerFilter(null, listOf("src/legacy"), listOf("src"))
        assertTrue(filter.accepts("src/Main.kt"))
        assertFalse(filter.accepts("scripts/run.sh"))
        assertFalse(filter.accepts("src/legacy/Old.kt"))
    }
}
