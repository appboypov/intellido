package dev.appboypov.intellido.todos.shared.views.todopanel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import dev.appboypov.intellido.core.services.IntelliDoBundle
import dev.appboypov.intellido.core.services.IntelliDoLog
import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException
import dev.appboypov.intellido.todos.shared.models.TodoList
import dev.appboypov.intellido.todos.shared.services.TodoRepository
import dev.appboypov.intellido.todos.shared.services.TodoTargets
import dev.appboypov.intellido.todos.shared.views.todosettings.TodoSettingsPage
import dev.appboypov.intellido.triggers.shared.services.TriggerCaptureService
import java.nio.file.Files
import java.nio.file.Path

/**
 * The action service (design D6): one registry from action name to handler. The panel, the IDE actions and the
 * dispatcher all run interactions through [run], so each has a single handler whether or not the panel is on screen.
 */
@Service(Service.Level.PROJECT)
class TodoPanelViewService(private val project: Project) {
    private val log = IntelliDoLog.of(TodoPanelViewService::class.java)
    private val repository get() = TodoRepository.getInstance(project)
    private var view: TodoPanelView? = null

    private val handlers: Map<String, (Map<String, String>) -> Any?> = mapOf(
        ADD to ::add,
        TOGGLE to { args -> repository.change { it.toggle(list(args), line(args)) } },
        EDIT to ::edit,
        OPEN to ::open,
        CREATE_LIST to ::createList,
        CLEANUP to { _ -> repository.cleanup().let { mapOf("removed" to it.removedTodos, "deletedLists" to it.deletedLists) } },
        SCAN to { _ -> TriggerCaptureService.getInstance(project).scanProject(); null },
        REFRESH to { _ -> repository.reload(); null },
        READ to { _ -> repository.read().map(::readout) },
        SHOW_PANEL to { _ -> showPanel() },
        CAPTURE to ::capture,
        OPEN_SETTINGS to { _ -> openSettings() },
    )

    /** Every registered action name. */
    val actionNames: Set<String> get() = handlers.keys

    /** Runs the handler registered for [name] with [args]. EDT. */
    fun run(name: String, args: Map<String, String> = emptyMap()): Any? {
        val handler = handlers[name] ?: throw TodoActionException("Unknown action: $name")
        log.debug("action", "name" to name)
        return handler(args)
    }

    fun attach(view: TodoPanelView) {
        this.view = view
    }

    fun detach(view: TodoPanelView) {
        if (this.view === view) this.view = null
    }

    /** Writes a PNG of the panel to `path`, or a temporary file, and returns its path. */
    private fun capture(args: Map<String, String>): Any {
        val view = view?.takeIf { it.isShowing } ?: throw TodoActionException("The IntelliDo panel is not visible; run $SHOW_PANEL first")
        val path = args["path"]?.let(Path::of) ?: Files.createTempFile("intellido-panel-", ".png")
        view.capture(path)
        return path.toAbsolutePath().toString()
    }

    /**
     * Adds a todo. `list` names a list file relative to the todos folder; otherwise `path` holds project-relative
     * files and folders, one per line (`.` for the project root). Without `text`, asks for it.
     */
    private fun add(args: Map<String, String>): Any? {
        val list = args["list"]
        val paths = args["path"]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (list == null && paths.isEmpty()) throw TodoActionException("Missing argument: path or list")
        val targets = paths.mapNotNull { relative ->
            val absolute = repository.projectRoot.resolve(relative).normalize()
            if (absolute.startsWith(repository.todosRoot)) null else TodoTargets.of(repository.projectRoot, absolute, Files.isDirectory(absolute))
        }
        if (list == null && targets.isEmpty()) throw TodoActionException("Nothing to add a todo to: every item is outside the project or in the todos folder")
        val text = args["text"] ?: prompt(targets.joinToString(", ") { it.file ?: it.folder }.ifEmpty { list!!.removeSuffix(".md") }) ?: return null
        return repository.change { store ->
            if (list != null) store.addToList(list, text).let { list } else targets.map { store.add(it, text) }
        }
    }

    private fun prompt(subject: String): String? = Messages.showInputDialog(
        project,
        IntelliDoBundle.message("prompt.add.message", subject),
        IntelliDoBundle.message("prompt.add.title"),
        null,
    )?.trim()?.ifEmpty { null }

    /** Creates a standalone list; without `name`, asks for it. */
    private fun createList(args: Map<String, String>): Any? {
        val name = args["name"] ?: Messages.showInputDialog(
            project,
            IntelliDoBundle.message("prompt.list.message"),
            IntelliDoBundle.message("prompt.list.title"),
            null,
        )?.trim()?.ifEmpty { null } ?: return null
        return repository.change { it.createList(name) }
    }

    /** Replaces a todo's text; without `text`, asks for it with the current text filled in. */
    private fun edit(args: Map<String, String>): Any? {
        val list = list(args)
        val line = line(args)
        val text = args["text"] ?: run {
            val todo = repository.store().read(list).todos.firstOrNull { it.line == line }
                ?: throw TodoActionException("Line ${line + 1} of $list is not a todo")
            Messages.showInputDialog(
                project,
                IntelliDoBundle.message("prompt.edit.message"),
                IntelliDoBundle.message("prompt.edit.title"),
                null,
                todo.text,
                null,
            )?.trim()?.ifEmpty { null }
        } ?: return null
        return repository.change { it.edit(list, line, text) }
    }

    /** Opens the file a todo is about, at its caught line; a todo without a file opens its list at the todo. */
    private fun open(args: Map<String, String>): Any {
        val list = list(args)
        val line = line(args)
        val store = repository.store()
        val todoList = store.read(list)
        val todo = todoList.todos.firstOrNull { it.line == line } ?: throw TodoActionException("Line ${line + 1} of $list is not a todo")
        val source = todo.file?.let { file -> todoList.folder?.let { TodoTargets.resolve(repository.projectRoot, it, file) } }
        val (path, target) = if (source != null && Files.exists(source)) source to ((todo.sourceLine ?: 1) - 1) else store.pathOf(list) to line
        navigate(path, target)
        return path.toString()
    }

    private fun navigate(path: Path, line: Int) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw TodoActionException("$path does not exist")
        OpenFileDescriptor(project, file, line.coerceAtLeast(0), 0).navigate(true)
    }

    private fun readout(list: TodoList): Map<String, Any?> = mapOf(
        "list" to list.relativePath,
        "folder" to list.folder,
        "title" to list.title,
        "todos" to list.todos.map {
            mapOf("line" to it.line, "done" to it.done, "file" to it.file, "sourceLine" to it.sourceLine, "text" to it.text)
        },
    )

    private fun showPanel(): Any? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TodoToolWindowFactory.ID)
            ?: throw TodoActionException("The IntelliDo tool window is not registered")
        toolWindow.activate(null)
        return null
    }

    private fun openSettings(): Any? {
        // The settings dialog is modal; open it after the caller returns.
        ApplicationManager.getApplication().invokeLater { ShowSettingsUtil.getInstance().showSettingsDialog(project, TodoSettingsPage::class.java) }
        return null
    }

    private fun list(args: Map<String, String>) = args["list"] ?: throw TodoActionException("Missing argument: list")

    private fun line(args: Map<String, String>) =
        args["line"]?.toIntOrNull() ?: throw TodoActionException("Missing or invalid argument: line (zero-based line in the list file)")

    companion object {
        const val ADD = "intellido.todo.add"
        const val TOGGLE = "intellido.todo.toggle"
        const val EDIT = "intellido.todo.edit"
        const val OPEN = "intellido.todo.open"
        const val CREATE_LIST = "intellido.list.create"
        const val CLEANUP = "intellido.cleanup.run"
        const val SCAN = "intellido.triggers.scan"
        const val REFRESH = "intellido.panel.refresh"
        const val READ = "intellido.panel.read"
        const val SHOW_PANEL = "intellido.panel.show"
        const val CAPTURE = "intellido.panel.capture"
        const val OPEN_SETTINGS = "intellido.settings.open"

        fun getInstance(project: Project): TodoPanelViewService = project.service()
    }
}
