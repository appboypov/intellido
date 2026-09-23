package dev.appboypov.intellido.todos.shared.services

import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException
import dev.appboypov.intellido.todos.shared.models.TodoTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TodoStoreTest {
    @TempDir
    lateinit var dir: Path

    private var clock = LocalDateTime.of(2026, 9, 23, 14, 5)
    private val store by lazy { TodoStore(dir.resolve("todos"), "shop") { clock } }

    @Test
    fun `a folder and its files share one list with frontmatter`() {
        store.add(TodoTarget("src/auth", null), "split the service")
        store.add(TodoTarget("src/auth", "Login.kt"), "rename login()")
        store.add(TodoTarget("src/auth", "Login.kt"), "fix redirect", sourceLine = 42)
        assertEquals(
            "---\nfolder: src/auth\n---\n\n- [ ] split the service\n- [ ] [[Login.kt]] rename login()\n- [ ] [[Login.kt]]:42 fix redirect\n",
            dir.resolve("todos/src/auth.md").readText(),
        )
        val todos = store.read("src/auth.md").todos
        assertEquals(listOf(null, "Login.kt", "Login.kt"), todos.map { it.file })
        assertEquals(listOf(null, null, 42), todos.map { it.sourceLine })
        assertEquals("fix redirect", todos[2].text)
    }

    @Test
    fun `the project root folder list is named after the project`() {
        store.add(TodoTarget(".", "README.md"), "update badges")
        val list = store.lists().single()
        assertEquals("shop.md", list.relativePath)
        assertEquals(".", list.folder)
        assertEquals("shop", list.title)
    }

    @Test
    fun `a standalone list has no folder and refuses an existing name`() {
        store.createList("Ideas")
        store.addToList("Ideas.md", "dark mode")
        assertEquals("- [ ] dark mode\n", dir.resolve("todos/Ideas.md").readText())
        assertEquals(null, store.read("Ideas.md").folder)
        assertThrows<TodoActionException> { store.createList("Ideas") }
        assertThrows<TodoActionException> { store.add(TodoTarget("Ideas", null), "x") }
    }

    @Test
    fun `completing stamps the line and reopening removes the stamp`() {
        store.add(TodoTarget("src", null), "ship it")
        assertTrue(store.toggle("src.md", 4))
        assertEquals("- [x] ship it ✅ 2026-09-23T14:05", dir.resolve("todos/src.md").readText().lines()[4])
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 5), store.read("src.md").todos.single().completedAt)
        assertFalse(store.toggle("src.md", 4))
        assertEquals("- [ ] ship it", dir.resolve("todos/src.md").readText().lines()[4])
        assertThrows<TodoActionException> { store.toggle("src.md", 0) }
    }

    @Test
    fun `cleanup removes old completed todos, keeps recent ones and stamps unstamped ones`() {
        val file = dir.resolve("todos/src.md")
        store.add(TodoTarget("src", null), "old")
        store.add(TodoTarget("src", null), "recent")
        store.add(TodoTarget("src", null), "open")
        file.writeText(
            file.readText()
                .replace("- [ ] old", "- [x] old ✅ 2026-09-22T13:00")
                .replace("- [ ] recent", "- [x] recent"),
        )
        val result = store.cleanup(Duration.ofHours(24))
        assertEquals(1, result.removedTodos)
        assertEquals(1, result.stampedTodos)
        assertEquals("---\nfolder: src\n---\n\n- [x] recent ✅ 2026-09-23T14:05\n- [ ] open\n", file.readText())
    }

    @Test
    fun `cleanup deletes lists left without todos and their empty folders`() {
        store.add(TodoTarget("src/auth", null), "done")
        store.toggle("src/auth.md", 4)
        clock = clock.plusDays(2)
        val result = store.cleanup(Duration.ofHours(24))
        assertEquals(listOf("src/auth.md"), result.deletedLists)
        assertFalse(dir.resolve("todos/src").exists())
        assertTrue(dir.resolve("todos").exists())
    }

    @Test
    fun `non-todo lines survive every change`() {
        val file = dir.resolve("todos/notes.md")
        file.parent.toFile().mkdirs()
        file.writeText("# Notes\n\nSome context.\n- [ ] first\n")
        store.addToList("notes.md", "second")
        store.toggle("notes.md", 3)
        store.cleanup(Duration.ofHours(24))
        assertEquals("# Notes\n\nSome context.\n- [x] first ✅ 2026-09-23T14:05\n- [ ] second\n", file.readText())
    }

    @Test
    fun `paths outside the todos folder are refused`() {
        assertThrows<TodoActionException> { store.addToList("../escape.md", "x") }
    }
}
