package dev.appboypov.intellido.triggers.shared.services

import dev.appboypov.intellido.triggers.shared.models.TriggerCapture
import dev.appboypov.intellido.triggers.shared.models.TriggerPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TriggerMatcherTest {
    private val default = TriggerPattern("//", true, "#todo", true, ";", true)

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "// fix login #todo;|fix login",
            "// #todo fix login;|fix login",
            "val x = 1 // #todo fix;|fix",
            "// see // #todo fix it ;  |fix it",
        ],
    )
    fun `default pattern captures the text between the markers`(line: String, text: String) {
        assertEquals(text, TriggerMatcher.match(line, 0, default)?.text)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "// #todo fix login",
            "// fix login;",
            "#todo fix; // later",
            "// #todo;",
            "code(); // #todo fix; more",
        ],
    )
    fun `default pattern ignores lines without a finished trigger`(line: String) {
        assertNull(TriggerMatcher.match(line, 0, default))
    }

    @Test
    fun `a trailing trigger keeps the code before it`() {
        assertEquals(TriggerCapture(4, "fix redirect", "redirect()"), TriggerMatcher.match("redirect() // #todo fix redirect;", 4, default))
    }

    @Test
    fun `a line left blank or holding only a list marker is deleted`() {
        assertNull(TriggerMatcher.match("    // #todo add logout;", 0, default)!!.remainder)
        assertNull(TriggerMatcher.match("- [ ] // #todo add logout;", 0, default)!!.remainder)
        assertNull(TriggerMatcher.match("1. // #todo add logout;", 0, default)!!.remainder)
    }

    @Test
    fun `a carriage return stays on the remainder`() {
        assertEquals("a()\r", TriggerMatcher.match("a() // #todo b;\r", 0, default)!!.remainder)
    }

    @Test
    fun `without a start marker the trigger begins at the contains marker`() {
        val pattern = default.copy(useStart = false)
        assertEquals(TriggerCapture(0, "fix login", "call()"), TriggerMatcher.match("call() #todo fix login;", 0, pattern))
    }

    @Test
    fun `without an end marker the trigger runs to the end of the line`() {
        val pattern = default.copy(useEnd = false)
        assertEquals(TriggerCapture(0, "fix login", "x()"), TriggerMatcher.match("x() // #todo fix login  ", 0, pattern))
    }

    @Test
    fun `without a contains marker the first start marker begins the trigger`() {
        val pattern = default.copy(useContains = false)
        assertEquals(TriggerCapture(0, "note // more", "x()"), TriggerMatcher.match("x() // note // more;", 0, pattern))
    }

    @Test
    fun `with only an end marker the whole line is the trigger`() {
        val pattern = default.copy(useStart = false, useContains = false)
        assertEquals(TriggerCapture(0, "do this", null), TriggerMatcher.match("  do this;", 0, pattern))
    }

    @Test
    fun `no switched-on marker captures nothing`() {
        val pattern = default.copy(useStart = false, useContains = false, useEnd = false)
        assertEquals(emptyList<TriggerCapture>(), TriggerMatcher.scan("// #todo a;", pattern))
    }

    @Test
    fun `scan and apply cut every trigger with one-based-ready line indexes`() {
        val text = "fun a() {\n    // #todo first;\n    b() // #todo second;\n}\n"
        val captures = TriggerMatcher.scan(text, default)
        assertEquals(listOf(TriggerCapture(1, "first", null), TriggerCapture(2, "second", "    b()")), captures)
        assertEquals("fun a() {\n    b()\n}\n", TriggerMatcher.apply(text, captures))
    }
}
