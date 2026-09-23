package dev.appboypov.intellido.triggers.shared.services

import dev.appboypov.intellido.core.services.IntelliDoLog
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Asks git which project files it ignores (spec: skip git-ignored files). Git answers from the work tree as it is
 * now, so a file written a moment ago is judged correctly. Blocking; call off the EDT.
 */
object GitIgnores {
    private val log = IntelliDoLog.of(GitIgnores::class.java)
    private const val TIMEOUT_SECONDS = 10L

    /**
     * The members of [paths] (relative to [root], `/`-separated) that git ignores. Empty when [root] is not in a git
     * work tree or git cannot run: then nothing counts as ignored.
     */
    fun ignored(root: Path, paths: Collection<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val process = try {
            ProcessBuilder("git", "check-ignore", "--stdin", "-z").directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (failure: Exception) {
            log.warn("git could not start; no file counts as ignored", failure)
            return emptySet()
        }
        process.outputStream.use { it.write(paths.joinToString("\u0000", postfix = "\u0000").toByteArray()) }
        val output = process.inputStream.use { String(it.readAllBytes()) }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            log.warn("git check-ignore timed out; no file counts as ignored", null, "root" to root)
            return emptySet()
        }
        // 0: some paths are ignored, 1: none are, 128: not a git work tree.
        return when (process.exitValue()) {
            0 -> output.split('\u0000').filter { it.isNotEmpty() }.toSet()
            else -> emptySet()
        }
    }
}
