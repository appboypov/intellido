package dev.appboypov.intellido.triggers.shared.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.appboypov.intellido.core.services.IntelliDoLog
import dev.appboypov.intellido.todos.shared.services.IntelliDoSettings
import dev.appboypov.intellido.todos.shared.services.TodoRepository
import dev.appboypov.intellido.todos.shared.services.TodoTargets
import dev.appboypov.intellido.triggers.shared.enums.TriggerDetection
import dev.appboypov.intellido.triggers.shared.models.TriggerCapture
import dev.appboypov.intellido.triggers.shared.models.TriggerPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

/**
 * Captures triggers from project files into todo lists (design D4): watches saved files or polls,
 * cuts each trigger from its file as an undoable command and records it with its line.
 */
@Service(Service.Level.PROJECT)
class TriggerCaptureService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val log = IntelliDoLog.of(TriggerCaptureService::class.java)
    private var detection: Disposable? = null
    private var pollJob: Job? = null

    /** (Re)starts detection with the current settings and scans the whole project once. */
    fun start() {
        stop()
        val settings = IntelliDoSettings.getInstance(project)
        when (settings.detection) {
            TriggerDetection.WATCH -> {
                val connection = Disposer.newDisposable(this, "IntelliDo trigger watch")
                project.messageBus.connect(connection).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) = onFilesChanged(events)
                })
                detection = connection
                scanProject()
            }
            TriggerDetection.POLL -> pollJob = scope.launch {
                while (true) {
                    scanProject()
                    delay(settings.pollSeconds.seconds)
                }
            }
        }
        log.info("trigger detection started", "mode" to settings.detection)
    }

    /**
     * Refreshes the project from disk, then scans every content file and captures in the files that hold triggers.
     * The refresh comes first so files changed outside the IDE are read as they are on disk.
     */
    fun scanProject() = ApplicationManager.getApplication().executeOnPooledThread {
        if (project.isDisposed) return@executeOnPooledThread
        VfsUtil.markDirtyAndRefresh(false, true, true, *ProjectRootManager.getInstance(project).contentRoots)
        val pattern = IntelliDoSettings.getInstance(project).pattern
        if (!pattern.isUsable) return@executeOnPooledThread
        val found = ReadAction.nonBlocking(Callable {
            val filter = filter()
            val found = mutableListOf<VirtualFile>()
            ProjectFileIndex.getInstance(project).iterateContent { file ->
                if (!file.isDirectory && accepts(file, filter) && holdsTrigger(file, pattern)) found += file
                true
            }
            found
        }).expireWith(this).executeSynchronously()
        val candidates = withoutGitIgnored(found)
        if (candidates.isNotEmpty()) scope.launch { withContext(Dispatchers.EDT) { capture(candidates) } }
    }

    override fun dispose() = stop()

    private fun stop() {
        detection?.let(Disposer::dispose)
        detection = null
        pollJob?.cancel()
        pollJob = null
    }

    private fun onFilesChanged(events: List<VFileEvent>) {
        val files = events.filter { it is VFileContentChangeEvent || it is VFileCreateEvent }.mapNotNull { it.file }.filter { !it.isDirectory }
        if (files.isEmpty()) return
        // VFS listeners run inside a write action: ask git on a pooled thread, edit the files on the EDT after it.
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            val accepted = ReadAction.nonBlocking(Callable {
                val filter = filter()
                files.filter { it.isValid && accepts(it, filter) }
            }).expireWith(this).executeSynchronously()
            val candidates = withoutGitIgnored(accepted)
            if (candidates.isNotEmpty()) scope.launch { withContext(Dispatchers.EDT) { capture(candidates) } }
        }
    }

    /** [files] less those git ignores, when skip git-ignored is on. Blocking. */
    private fun withoutGitIgnored(files: List<VirtualFile>): List<VirtualFile> {
        if (files.isEmpty() || !IntelliDoSettings.getInstance(project).skipGitIgnored) return files
        val root = TodoRepository.getInstance(project).projectRoot
        val byPath = files.associateBy { root.relativize(it.toNioPath()).invariantSeparatorsPathString }
        val ignored = GitIgnores.ignored(root, byPath.keys)
        return byPath.filterKeys { it !in ignored }.values.toList()
    }

    /** Cuts the triggers from [files] and records them. EDT. */
    private fun capture(files: List<VirtualFile>) {
        val pattern = IntelliDoSettings.getInstance(project).pattern
        val documents = FileDocumentManager.getInstance()
        val repository = TodoRepository.getInstance(project)
        for (file in files) {
            if (!file.isValid || !file.isWritable) continue
            val document = documents.getDocument(file) ?: continue
            val captures = TriggerMatcher.scan(document.text, pattern)
            if (captures.isEmpty()) continue
            val target = TodoTargets.of(repository.projectRoot, file.toNioPath(), isDirectory = false) ?: continue
            WriteCommandAction.runWriteCommandAction(project, COMMAND_NAME, null, { cut(document, captures) })
            documents.saveDocument(document)
            runCatching {
                repository.change { store -> captures.forEach { store.add(target, it.text, sourceLine = it.line + 1) } }
            }.onFailure { log.error("recording captured todos failed", it, "file" to file.path) }
            log.info("captured todos", "file" to file.path, "count" to captures.size)
        }
    }

    /** Applies [captures] to [document] from the bottom up so earlier line numbers stay valid. */
    private fun cut(document: Document, captures: List<TriggerCapture>) {
        for (capture in captures.sortedByDescending { it.line }) {
            if (capture.line >= document.lineCount) continue
            val start = document.getLineStartOffset(capture.line)
            val end = document.getLineEndOffset(capture.line)
            val remainder = capture.remainder
            when {
                remainder != null -> document.replaceString(start, end, remainder)
                capture.line + 1 < document.lineCount -> document.deleteString(start, document.getLineStartOffset(capture.line + 1))
                capture.line > 0 -> document.deleteString(document.getLineEndOffset(capture.line - 1), end)
                else -> document.deleteString(start, end)
            }
        }
    }

    private fun filter(): TriggerFilter {
        val settings = IntelliDoSettings.getInstance(project)
        val repository = TodoRepository.getInstance(project)
        val todos = repository.todosRoot.takeIf { it.startsWith(repository.projectRoot) }
            ?.let { repository.projectRoot.relativize(it).invariantSeparatorsPathString }
        return TriggerFilter(todos, settings.ignore, settings.whitelist)
    }

    /** Whether the settings' filters let IntelliDo scan [file]; git-ignored files are left to [withoutGitIgnored]. */
    private fun accepts(file: VirtualFile, filter: TriggerFilter): Boolean {
        if (!file.isInLocalFileSystem || file.fileType.isBinary) return false
        if (!ProjectFileIndex.getInstance(project).isInContent(file)) return false
        val root = TodoRepository.getInstance(project).projectRoot
        val path = file.toNioPath()
        return path.startsWith(root) && filter.accepts(root.relativize(path).invariantSeparatorsPathString)
    }

    private fun holdsTrigger(file: VirtualFile, pattern: TriggerPattern): Boolean {
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.text
            ?: runCatching { VfsUtilCore.loadText(file) }.getOrNull()
            ?: return false
        return TriggerMatcher.scan(text, pattern).isNotEmpty()
    }

    companion object {
        const val COMMAND_NAME = "Capture Todos"

        fun getInstance(project: Project): TriggerCaptureService = project.service()
    }
}
