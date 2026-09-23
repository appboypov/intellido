package dev.appboypov.intellido.todos.shared.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.appboypov.intellido.core.services.IntelliDoLog
import dev.appboypov.intellido.todos.shared.models.CleanupResult
import dev.appboypov.intellido.todos.shared.models.TodoList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours

/**
 * The project's todo lists (design D2): wraps [TodoStore] with the IDE, publishes the lists,
 * follows edits to the todos folder and runs scheduled cleanup (design D5).
 */
@Service(Service.Level.PROJECT)
class TodoRepository(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val log = IntelliDoLog.of(TodoRepository::class.java)
    private val mutableLists = MutableStateFlow<List<TodoList>>(emptyList())
    private val reloadQueued = AtomicBoolean(false)
    private var cleanupJob: Job? = null

    /** Every list in the todos folder, as last read. */
    val lists: StateFlow<List<TodoList>> = mutableLists.asStateFlow()

    /** The project root folder. */
    val projectRoot: Path
        get() = project.guessProjectDir()?.toNioPath() ?: Path.of(project.basePath ?: System.getProperty("user.home"))

    /** The configured todos folder, resolved against the project root. */
    val todosRoot: Path
        get() = projectRoot.resolve(IntelliDoSettings.getInstance(project).todosFolder).normalize()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val root = todosRoot.toString()
                if (events.any { it.path.startsWith(root) }) queueReload()
            }
        })
    }

    /** Cleans up now when the interval has passed since the last run, then checks again every hour. */
    fun start() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - TodoCleanupState.getInstance(project).lastRunMillis
                if (elapsed >= IntelliDoSettings.getInstance(project).cleanupInterval.toMillis()) {
                    withContext(Dispatchers.EDT) { runCatching { cleanup() }.onFailure { log.error("scheduled cleanup failed", it) } }
                }
                delay(1.hours)
            }
        }
    }

    /** Runs [change] on the store with unsaved list edits saved first, then refreshes the IDE's view of the files. EDT. */
    fun <T> change(change: (TodoStore) -> T): T {
        saveListDocuments()
        val result = change(store())
        refresh()
        return result
    }

    /** Reads the lists as they are on disk now. */
    fun read(): List<TodoList> = store().lists()

    /** Re-reads the lists and publishes them. */
    fun reload() {
        mutableLists.value = runCatching { read() }.getOrElse {
            log.warn("reading the todo lists failed", it, "root" to todosRoot)
            emptyList()
        }
    }

    /** Removes every completed todo and deletes emptied lists. EDT. */
    fun cleanup(): CleanupResult {
        TodoCleanupState.getInstance(project).lastRunMillis = System.currentTimeMillis()
        return change { it.cleanup() }.also {
            log.info("cleanup", "removed" to it.removedTodos, "deleted" to it.deletedLists.size)
        }
    }

    fun store() = TodoStore(todosRoot, project.name)

    override fun dispose() = Unit

    private fun refresh() {
        reload()
        // Finds a newly created todos folder too; the recursive refresh then shows every changed list in the IDE.
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(todosRoot)?.let { VfsUtil.markDirtyAndRefresh(true, true, true, it) }
    }

    private fun queueReload() {
        if (!reloadQueued.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            reloadQueued.set(false)
            reload()
        }
    }

    private fun saveListDocuments() {
        val documents = FileDocumentManager.getInstance()
        val root = todosRoot.toString()
        documents.unsavedDocuments
            .filter { documents.getFile(it)?.path?.startsWith(root) == true }
            .forEach(documents::saveDocument)
    }

    companion object {
        fun getInstance(project: Project): TodoRepository = project.service()
    }
}
