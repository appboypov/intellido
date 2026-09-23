package dev.appboypov.intellido.todos.shared.views.todopanel

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.appboypov.intellido.core.services.IntelliDoBundle
import dev.appboypov.intellido.todos.shared.exceptions.TodoActionException
import dev.appboypov.intellido.todos.shared.models.Todo
import dev.appboypov.intellido.todos.shared.models.TodoList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** The IntelliDo panel: a quick-add field over a tree of lists and their todos. */
class TodoPanelView(private val project: Project, private val viewModel: TodoPanelViewModel, parent: Disposable) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT + ModalityState.any().asContextElement())
    private val root = CheckedTreeNode()
    private val collapsed = mutableSetOf<String>()
    private var rebuilding = false

    private val tree = object : CheckboxTree(Renderer(), root, CheckboxTreeBase.CheckPolicy(false, false, false, false)) {
        override fun onNodeStateChanged(node: CheckedTreeNode?) {
            val todo = node?.userObject as? TodoNode ?: return
            if (!rebuilding) act { viewModel.toggle(todo.list.relativePath, todo.todo.line) }
        }
    }

    private val quickAdd = JBTextField().apply {
        emptyText.text = IntelliDoBundle.message("panel.quickAdd.placeholder")
        addActionListener {
            val text = text.trim()
            if (text.isEmpty()) return@addActionListener
            act { viewModel.add(text, selectedList()) }
            this.text = ""
        }
    }

    val component = JPanel(BorderLayout()).apply {
        add(JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(4); add(quickAdd) }, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    }

    /** The component that takes keyboard focus when the panel is activated. */
    val focusTarget = quickAdd

    val isShowing: Boolean get() = component.isShowing

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.emptyText.text = IntelliDoBundle.message("panel.empty")
        tree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent) {
                if (!rebuilding) listKey(event.path)?.let(collapsed::remove)
            }

            override fun treeCollapsed(event: TreeExpansionEvent) {
                if (!rebuilding) listKey(event.path)?.let(collapsed::add)
            }
        })
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val todo = todoAt(event.x, event.y) ?: return false
                act { viewModel.open(todo.list.relativePath, todo.todo.line) }
                return true
            }
        }.installOn(tree)
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(component: Component, x: Int, y: Int) {
                val todo = todoAt(x, y) ?: return
                tree.selectionPath = tree.getPathForRow(tree.getClosestRowForLocation(x, y))
                val group = DefaultActionGroup(
                    DumbAwareAction.create(IntelliDoBundle.message("panel.menu.edit")) { act { viewModel.edit(todo.list.relativePath, todo.todo.line) } },
                )
                ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group).component.show(component, x, y)
            }
        })
        viewModel.attach(this)
        Disposer.register(parent) {
            scope.cancel()
            viewModel.detach(this)
        }
        scope.launch { viewModel.lists.collect(::render) }
        viewModel.refresh()
    }

    /** Writes a PNG of what the panel shows now to [path]. EDT. */
    fun capture(path: Path) {
        val image = UIUtil.createImage(component, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            component.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(image, "png", path.toFile())
    }

    /** The todo on the row at [x], [y], anywhere across the row's width, or null. */
    private fun todoAt(x: Int, y: Int): TodoNode? {
        val row = tree.getClosestRowForLocation(x, y)
        val bounds = tree.getRowBounds(row) ?: return null
        if (y < bounds.y || y >= bounds.y + bounds.height) return null
        return tree.getPathForRow(row)?.let(::nodeOf) as? TodoNode
    }

    private fun render(lists: List<TodoList>) {
        val selected = tree.selectionPath?.let(::nodeOf)?.let(::keyOf)
        rebuilding = true
        try {
            root.removeAllChildren()
            var selection: TreePath? = null
            for (list in lists) {
                val listNode = DefaultMutableTreeNode(ListNode(list))
                root.add(listNode)
                for (todo in list.todos) {
                    listNode.add(CheckedTreeNode(TodoNode(list, todo)).apply { isChecked = todo.done })
                }
            }
            (tree.model as DefaultTreeModel).reload()
            for (index in 0 until root.childCount) {
                val listNode = root.getChildAt(index) as DefaultMutableTreeNode
                val path = TreePath(arrayOf(root, listNode))
                val list = (listNode.userObject as ListNode).list
                if (list.relativePath !in collapsed) tree.expandPath(path)
                if (keyOf(listNode.userObject) == selected) selection = path
                for (child in 0 until listNode.childCount) {
                    val todoNode = listNode.getChildAt(child) as DefaultMutableTreeNode
                    if (keyOf(todoNode.userObject) == selected) selection = path.pathByAddingChild(todoNode)
                }
            }
            selection?.let { tree.selectionPath = it }
        } finally {
            rebuilding = false
        }
    }

    /** The list the selected row belongs to, or null with nothing selected. */
    private fun selectedList(): String? = when (val node = tree.selectionPath?.let(::nodeOf)) {
        is ListNode -> node.list.relativePath
        is TodoNode -> node.list.relativePath
        else -> null
    }

    private fun act(action: () -> Any?) {
        try {
            action()
        } catch (failure: TodoActionException) {
            Messages.showErrorDialog(project, failure.message, IntelliDoBundle.message("error.title"))
            viewModel.refresh()
        }
    }

    private fun nodeOf(path: TreePath): Any? = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun listKey(path: TreePath): String? = (nodeOf(path) as? ListNode)?.list?.relativePath

    private fun keyOf(node: Any?): String? = when (node) {
        is ListNode -> node.list.relativePath
        is TodoNode -> "${node.list.relativePath}#${node.todo.line}"
        else -> null
    }

    private class ListNode(val list: TodoList)

    private companion object {
        const val POPUP_PLACE = "IntelliDoPanelPopup"
    }

    private class TodoNode(val list: TodoList, val todo: Todo)

    private class Renderer : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            val node = (value as? DefaultMutableTreeNode)?.userObject
            val text = textRenderer
            when (node) {
                is ListNode -> {
                    text.icon = if (node.list.folder != null) AllIcons.Nodes.Folder else AllIcons.Nodes.Favorite
                    text.append(node.list.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    val open = node.list.todos.count { !it.done }
                    text.append("  $open/${node.list.todos.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is TodoNode -> {
                    val todo = node.todo
                    todo.file?.let { file ->
                        text.icon = FileTypeManager.getInstance().getFileTypeByFileName(file).icon
                        text.append(file + (todo.sourceLine?.let { ":$it" } ?: "") + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                    text.append(
                        todo.text,
                        if (todo.done) SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, UIUtil.getInactiveTextColor()) else SimpleTextAttributes.REGULAR_ATTRIBUTES,
                    )
                }
            }
        }
    }
}
