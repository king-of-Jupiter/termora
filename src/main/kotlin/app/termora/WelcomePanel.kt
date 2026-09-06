package app.termora

import app.termora.actions.*
import app.termora.database.DatabaseManager
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.terminal.DataKey
import app.termora.tree.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.util.UIScale
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.max

class WelcomePanel(
    private val embedTree: Boolean = true,
    private val externalHostTreeProvider: (() -> NewHostTree?)? = null,
) : JPanel(BorderLayout()), Disposable, TerminalTab, DataProvider {
    private val properties get() = DatabaseManager.getInstance().properties
    private val rootPanel = JPanel(BorderLayout())
    private val hostTree = NewHostTree()
    private val countLabel = JLabel().apply {
        font = font.deriveFont(UIScale.scale(12f))
        foreground = HostViewStyle.secondary
    }
    private val hostCardsPanel = HostCardsPanel(
        hostTreeProvider = { currentHostTree() },
        onHostCountChanged = { visible, total ->
            countLabel.text = if (visible == total) I18n.getString("termora.welcome.host-count", total)
            else I18n.getString("termora.welcome.filtered-count", visible, total)
        },
    )
    private val centerCardLayout = CardLayout()
    private val centerPanel = JPanel(centerCardLayout)
    private val toolbar = JPanel(BorderLayout(UIScale.scale(8), UIScale.scale(8)))
    private val toolbarActions = JPanel()
    private var stackedToolbar = false
    private var fullContent = properties.getString("WelcomeFullContent", "false").toBoolean()
    private var viewMode = if (embedTree) properties.getString("WelcomeViewMode", "cards") else "cards"
    private val dataProviderSupport = DataProviderSupport()
    private val filterableTreeModel = FilterableTreeModel(hostTree).apply { expand = true }
    private var lastFocused: Component? = null
    private val searchTextField = filterableTreeModel.filterableTextField

    init {
        initView()
        initEvents()
    }

    private fun currentHostTree(): NewHostTree? = if (embedTree) hostTree else externalHostTreeProvider?.invoke()

    private fun initView() {
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, false)
        putClientProperty(FindEverywhereProvider.SKIP_FIND_EVERYWHERE, true)
        background = HostViewStyle.background
        rootPanel.isOpaque = false
        centerPanel.isOpaque = false
        val heading = JPanel(BorderLayout(UIScale.scale(16), 0)).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, UIScale.scale(2), UIScale.scale(18), 0)
        }
        val title = JLabel(I18n.getString("termora.welcome.hosts")).apply {
            font = font.deriveFont(Font.BOLD, UIScale.scale(26f))
            foreground = HostViewStyle.foreground
        }
        val titleRow = JPanel(BorderLayout(UIScale.scale(16), 0)).apply {
            isOpaque = false
            add(title, BorderLayout.WEST)
            add(countLabel, BorderLayout.CENTER)
        }
        heading.add(titleRow, BorderLayout.CENTER)
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(heading, BorderLayout.NORTH)
            add(createSearchPanel(), BorderLayout.CENTER)
            border = BorderFactory.createEmptyBorder(0, 0, UIScale.scale(24), 0)
        }
        rootPanel.add(top, BorderLayout.NORTH)
        rootPanel.add(createHostPanel(), BorderLayout.CENTER)
        add(rootPanel, BorderLayout.CENTER)
        if (embedTree) dataProviderSupport.addData(DataProviders.Welcome.HostTree, hostTree)
        perform()
    }

    private fun createSearchPanel(): JComponent {
        searchTextField.font = searchTextField.font.deriveFont(UIScale.scale(14f))
        searchTextField.preferredSize = UIScale.scale(Dimension(340, 38))
        searchTextField.minimumSize = UIScale.scale(Dimension(140, 38))
        searchTextField.placeholderText = I18n.getString("termora.welcome.search-placeholder")
        searchTextField.accessibleContext.accessibleName = searchTextField.placeholderText
        searchTextField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.find)
        searchTextField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
        searchTextField.putClientProperty(FlatClientProperties.STYLE, mapOf(
            "arc" to AppUi.radius,
            "margin" to Insets(0, 12, 0, 12),
            "background" to HostViewStyle.surface,
            "foreground" to HostViewStyle.foreground,
            "placeholderForeground" to HostViewStyle.secondary,
            "borderColor" to HostViewStyle.surface,
            "focusedBorderColor" to HostViewStyle.accent,
            "focusColor" to HostViewStyle.accent,
            "focusWidth" to 1,
        ))
        searchTextField.actionMap.put("beep", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = Unit
        })
        searchTextField.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-search")
        searchTextField.actionMap.put("clear-search", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { searchTextField.text = StringUtils.EMPTY }
        })

        val newHost = FlatButton().apply {
            text = I18n.getString("termora.welcome.new-host")
            icon = Icons.add
            iconTextGap = UIScale.scale(6)
            font = font.deriveFont(Font.BOLD, UIScale.scale(12f))
            preferredSize = UIScale.scale(Dimension(136, 36))
            minimumSize = preferredSize
            maximumSize = preferredSize
            alignmentY = Component.CENTER_ALIGNMENT
            putClientProperty(FlatClientProperties.STYLE, mapOf(
                "arc" to AppUi.radius,
                "background" to AppUi.accent,
                "hoverBackground" to AppUi.accentHover,
                "pressedBackground" to AppUi.accentHover,
                "foreground" to Color.WHITE,
                "borderWidth" to 0,
                "focusedBorderColor" to HostViewStyle.accent,
            ))
            addActionListener { e -> ActionManager.getInstance().getAction(NewHostAction.NEW_HOST)?.actionPerformed(e) }
        }

        toolbarActions.isOpaque = false
        toolbarActions.layout = BoxLayout(toolbarActions, BoxLayout.X_AXIS)
        toolbarActions.add(Box.createHorizontalGlue())
        if (embedTree) {
            val viewButtons = ButtonGroup()
            val views = JPanel(java.awt.GridLayout(1, 2, UIScale.scale(2), 0)).apply {
                isOpaque = true
                background = HostViewStyle.background
                border = BorderFactory.createEmptyBorder(UIScale.scale(2), UIScale.scale(2), UIScale.scale(2), UIScale.scale(2))
                preferredSize = UIScale.scale(Dimension(166, 36))
                minimumSize = preferredSize
                maximumSize = preferredSize
                alignmentY = Component.CENTER_ALIGNMENT
            }
            for ((mode, key) in listOf("cards" to "termora.welcome.view.cards", "tree" to "termora.welcome.view.list")) {
                val button = JToggleButton(I18n.getString(key)).apply {
                    isSelected = viewMode == mode
                    font = font.deriveFont(UIScale.scale(12f))
                    margin = UIScale.scale(Insets(0, 8, 0, 8))
                    putClientProperty(FlatClientProperties.STYLE, mapOf(
                        "arc" to AppUi.radius - 2,
                        "background" to AppUi.surfaceSoft,
                        "foreground" to HostViewStyle.secondary,
                        "hoverBackground" to HostViewStyle.hover,
                        "pressedBackground" to HostViewStyle.hover,
                        "selectedBackground" to HostViewStyle.surface,
                        "selectedForeground" to HostViewStyle.foreground,
                        "borderWidth" to 0,
                        "focusWidth" to 0,
                    ))
                    addActionListener {
                        viewMode = mode
                        centerCardLayout.show(centerPanel, mode)
                        perform()
                    }
                }
                viewButtons.add(button)
                views.add(button)
            }
            toolbarActions.add(views)
            toolbarActions.add(Box.createHorizontalStrut(UIScale.scale(8)))
        }
        toolbarActions.add(newHost)
        toolbarActions.add(Box.createHorizontalStrut(UIScale.scale(4)))
        val more = FlatButton().apply {
            icon = Icons.moreHorizontal
            toolTipText = I18n.getString("termora.welcome.workspace-actions")
            accessibleContext.accessibleName = toolTipText
            preferredSize = UIScale.scale(Dimension(36, 36))
            minimumSize = preferredSize
            maximumSize = preferredSize
            alignmentY = Component.CENTER_ALIGNMENT
            putClientProperty(FlatClientProperties.STYLE, mapOf(
                "arc" to AppUi.radius,
                "background" to HostViewStyle.surface,
                "hoverBackground" to HostViewStyle.hover,
                "pressedBackground" to HostViewStyle.hover,
                "borderWidth" to 0,
                "focusWidth" to 0,
            ))
            addActionListener { showWorkspaceMenu(this) }
        }
        toolbarActions.add(more)
        toolbar.isOpaque = true
        toolbar.background = HostViewStyle.surface
        toolbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(HostViewStyle.border, UIScale.scale(1), true),
            BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(7), UIScale.scale(6), UIScale.scale(7)),
        )
        toolbar.add(searchTextField, BorderLayout.CENTER)
        toolbar.add(toolbarActions, BorderLayout.EAST)
        return toolbar
    }

    private fun showWorkspaceMenu(invoker: JComponent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem(I18n.getString(
            if (fullContent) "termora.welcome.center-content" else "termora.welcome.fill-window"
        )).apply {
            icon = if (fullContent) Icons.collapseAll else Icons.expandAll
            addActionListener { fullContent = !fullContent; perform() }
        })
        menu.addSeparator()
        menu.add(JMenuItem(I18n.getString("termora.welcome.workspace-actions")).apply {
            icon = Icons.moreHorizontal
            addActionListener { currentHostTree()?.showContextmenuForRoot(invoker, 0, invoker.height) }
        })
        menu.show(invoker, 0, invoker.height)
    }

    private fun createHostPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply { isOpaque = false }
        // Отключить звук при backspace в дереве без выделенного узла
        hostTree.actionMap.put("beep", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {}
        })
        hostTree.actionMap.put("find", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                searchTextField.requestFocusInWindow()
            }
        })
        hostTree.showsRootHandles = true

        val scrollPane = JScrollPane(hostTree)
        scrollPane.viewport.background = HostViewStyle.background
        hostTree.background = HostViewStyle.background
        hostTree.foreground = HostViewStyle.foreground
        scrollPane.border = BorderFactory.createEmptyBorder()


        panel.add(scrollPane, BorderLayout.CENTER)
        panel.border = BorderFactory.createEmptyBorder()

        hostTree.model = filterableTreeModel
        hostTree.name = "WelcomeHostTree"
        hostTree.restoreExpansions()

        // 卡片视图为默认；树视图仅在内嵌树时可切换
        centerPanel.add(hostCardsPanel, "cards")
        if (embedTree) {
            centerPanel.add(panel, "tree")
        }
        centerCardLayout.show(centerPanel, if (embedTree) viewMode else "cards")

        return centerPanel
    }


    private fun initEvents() {

        Disposer.register(this, hostTree)
        Disposer.register(this, hostCardsPanel)
        Disposer.register(hostTree, filterableTreeModel)

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
            "focus-search",
        )
        actionMap.put("focus-search", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                searchTextField.requestFocusInWindow()
                searchTextField.selectAll()
            }
        })

        // 搜索框同时过滤卡片视图
        searchTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                hostCardsPanel.filter(searchTextField.text)
            }
        })

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                perform()
            }

            override fun componentShown(e: ComponentEvent) {
                perform()
                removeComponentListener(this)
            }
        })


        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                perform()
            }
        })

        filterableTreeModel.addFilter(object : Filter {
            override fun filter(node: Any): Boolean {
                val text = searchTextField.text.trim()
                if (text.isBlank()) return true
                if (node !is HostTreeNode) return false
                if (node is TeamTreeNode || node.id == "0") return true
                return node.host.name.contains(text, ignoreCase = true)
                        || node.host.host.contains(text, ignoreCase = true)
                        || node.host.username.contains(text, ignoreCase = true)
                        || node.host.remark.contains(text, ignoreCase = true)
            }

            override fun canFilter(): Boolean {
                return searchTextField.text.trim().isNotBlank()
            }

        })

        searchTextField.addKeyListener(object : KeyAdapter() {
            private val event = ActionEvent(hostTree, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY)
            private val openHostAction get() = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_DOWN || e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_UP) {
                    if (viewMode == "cards") {
                        when (e.keyCode) {
                            KeyEvent.VK_ENTER -> hostCardsPanel.openFirstHost(e)
                            else -> hostCardsPanel.focusHost(last = e.keyCode == KeyEvent.VK_UP)
                        }
                        e.consume()
                        return
                    }
                    when (e.keyCode) {
                        KeyEvent.VK_UP -> hostTree.actionMap.get("selectPrevious")?.actionPerformed(event)
                        KeyEvent.VK_DOWN -> hostTree.actionMap.get("selectNext")?.actionPerformed(event)
                        else -> {
                            for (node in hostTree.getSelectionSimpleTreeNodes(true)) {
                                openHostAction?.actionPerformed(OpenHostActionEvent(hostTree, node.host, e))
                            }
                        }
                    }
                    e.consume()
                }
            }
        })

    }

    private fun perform() {
        val side = if (fullContent) UIScale.scale(24)
        else max(UIScale.scale(28), (width - UIScale.scale(1280)) / 2)
        rootPanel.border = BorderFactory.createEmptyBorder(UIScale.scale(30), side, UIScale.scale(28), side)
        if (width > 0) {
            val stack = width - side * 2 < UIScale.scale(700)
            if (stack != stackedToolbar) {
                stackedToolbar = stack
                toolbar.remove(toolbarActions)
                toolbar.add(toolbarActions, if (stack) BorderLayout.SOUTH else BorderLayout.EAST)
            }
        }
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    override fun getTitle(): String {
        return StringUtils.EMPTY
    }

    override fun getIcon(): Icon {
        return Icons.homeFolder
    }

    override fun getJComponent(): JComponent {
        return this
    }

    override fun canReconnect(): Boolean {
        return false
    }

    override fun canClose(): Boolean {
        return false
    }

    override fun canClone(): Boolean {
        return false
    }

    override fun onLostFocus() {
        lastFocused = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    }

    override fun onGrabFocus() {
        SwingUtilities.invokeLater { lastFocused?.requestFocusInWindow() }
    }

    override fun dispose() {
        properties.putString("WelcomeFullContent", fullContent.toString())
        if (embedTree) {
            properties.putString("WelcomeViewMode", viewMode)
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey)
    }


}
