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
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TodoStoreTest {
    @TempDir
    lateinit var dir: Path

    private val store by lazy { TodoStore(dir.resolve("todos"), "shop") }

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
    fun `completing and reopening only change the checkbox`() {
        store.add(TodoTarget("src/auth", "Login.kt"), "fix redirect", sourceLine = 42)
        assertTrue(store.toggle("src/auth.md", 4))
        assertEquals("- [x] [[Login.kt]]:42 fix redirect", dir.resolve("todos/src/auth.md").readText().lines()[4])
        assertFalse(store.toggle("src/auth.md", 4))
        assertEquals("- [ ] [[Login.kt]]:42 fix redirect", dir.resolve("todos/src/auth.md").readText().lines()[4])
        assertThrows<TodoActionException> { store.toggle("src/auth.md", 0) }
    }

    @Test
    fun `cleanup removes every completed todo and keeps open ones`() {
        val file = dir.resolve("todos/src.md")
        store.add(TodoTarget("src", null), "done")
        store.add(TodoTarget("src", null), "open")
        store.toggle("src.md", 4)
        val result = store.cleanup()
        assertEquals(1, result.removedTodos)
        assertEquals("---\nfolder: src\n---\n\n- [ ] open\n", file.readText())
    }

    @Test
    fun `cleanup deletes lists left without todos and their empty folders`() {
        store.add(TodoTarget("src/auth", null), "done")
        store.toggle("src/auth.md", 4)
        val result = store.cleanup()
        assertEquals(listOf("src/auth.md"), result.deletedLists)
        assertFalse(dir.resolve("todos/src").exists())
        assertTrue(dir.resolve("todos").exists())
    }

    @Test
    fun `editing replaces the text and keeps the box and file link`() {
        store.add(TodoTarget("src/auth", "Login.kt"), "fix redirect", sourceLine = 42)
        store.toggle("src/auth.md", 4)
        store.edit("src/auth.md", 4, "  fix the login redirect ")
        assertEquals("- [x] [[Login.kt]]:42 fix the login redirect", dir.resolve("todos/src/auth.md").readText().lines()[4])
        assertThrows<TodoActionException> { store.edit("src/auth.md", 4, " ") }
    }

    @Test
    fun `non-todo lines survive every change`() {
        val file = dir.resolve("todos/notes.md")
        file.parent.toFile().mkdirs()
        file.writeText("# Notes\n\nSome context.\n- [ ] first\n")
        store.addToList("notes.md", "second")
        store.toggle("notes.md", 3)
        store.cleanup()
        assertEquals("# Notes\n\nSome context.\n- [ ] second\n", file.readText())
    }

    @Test
    fun `paths outside the todos folder are refused`() {
        assertThrows<TodoActionException> { store.addToList("../escape.md", "x") }
    }
}
