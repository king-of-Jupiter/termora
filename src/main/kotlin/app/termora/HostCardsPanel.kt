package app.termora

import app.termora.account.AccountManager
import app.termora.actions.OpenHostAction
import app.termora.database.DataType
import app.termora.database.DatabaseChangedExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.plugin.internal.ssh.OSDetector
import app.termora.protocol.ProtocolProvider
import app.termora.tree.NewHostTree
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.util.UIScale
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.Timer

class HostCardsPanel(
    private val hostTreeProvider: () -> NewHostTree? = { null },
    private val onHostCountChanged: (visible: Int, total: Int) -> Unit = { _, _ -> },
) : JPanel(BorderLayout()), Disposable {
    private val hostManager get() = HostManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()
    private val actionManager get() = ActionManager.getInstance()
    private val hostCards = mutableListOf<HostCard>()
    private val contentPanel = object : JPanel(GridBagLayout()), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
        override fun getScrollableUnitIncrement(rect: Rectangle, orientation: Int, direction: Int): Int = UIScale.scale(24)
        override fun getScrollableBlockIncrement(rect: Rectangle, orientation: Int, direction: Int): Int =
            (rect.height - UIScale.scale(96)).coerceAtLeast(UIScale.scale(24))
    }
    private val scrollPane = JScrollPane(contentPanel)
    private var filterText: String = StringUtils.EMPTY
    private var draggingCard: HostCard? = null
    private var dropGroup: HostGroupPanel? = null
    private var dropCard: HostCard? = null
    private var dropIndex: Int = -1
    private var dropAfter: Boolean = false
    private var dragGhost: DragGhost? = null

    init {
        isOpaque = false
        contentPanel.isOpaque = false
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.viewport.isOpaque = false
        scrollPane.isOpaque = false
        add(scrollPane, BorderLayout.CENTER)
        scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                contentPanel.revalidate()
            }
        })
        contentPanel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = showRootMenu(e)
            override fun mouseReleased(e: MouseEvent) = showRootMenu(e)
            private fun showRootMenu(e: MouseEvent) {
                if (e.isPopupTrigger) hostTreeProvider()?.showContextmenuForRoot(contentPanel, e.x, e.y)
            }
        })
        DynamicExtensionHandler.getInstance()
            .register(DatabaseChangedExtension::class.java, object : DatabaseChangedExtension {
                override fun onDataChanged(
                    id: String, type: String, action: DatabaseChangedExtension.Action,
                    source: DatabaseChangedExtension.Source,
                ) {
                    if (type.isNotBlank() && type != DataType.Host.name) return
                    SwingUtilities.invokeLater { rebuild() }
                }
            }).let { Disposer.register(this, it) }
        DynamicExtensionHandler.getInstance()
            .register(ThemeChangeExtension::class.java, object : ThemeChangeExtension {
                override fun onChanged() = rebuild()
            }).let { Disposer.register(this, it) }
        rebuild()
    }

    fun filter(text: String) {
        val query = text.trim()
        if (query == filterText) return
        filterText = query
        rebuild()
        scrollPane.verticalScrollBar.value = 0
    }

    fun focusHost(last: Boolean = false) {
        val card = if (last) hostCards.lastOrNull() else hostCards.firstOrNull()
        card?.requestFocusInWindow()
    }

    fun openFirstHost(event: InputEvent) {
        hostCards.firstOrNull()?.open(event)
    }

    private fun matches(host: Host): Boolean = filterText.isBlank() ||
        listOf(host.name, host.host, host.username, host.remark).any { it.contains(filterText, ignoreCase = true) }

    private fun rebuild() {
        removeDragGhost()
        clearDropTarget()
        draggingCard = null
        hostCards.forEach { it.stopAnimations() }
        hostCards.clear()
        contentPanel.removeAll()
        val ownerIds = accountManager.getOwnerIds()
        val all = hostManager.hosts().filter { it.ownerId in ownerIds && !it.isTemporary }
        val folders = all.filter { it.isFolder }.sortedBy { it.sort }
        val folderIds = folders.map { it.id }.toSet()
        val totalHosts = all.filterNot { it.isFolder }
        val visibleHosts = totalHosts.filter(::matches)
        onHostCountChanged(visibleHosts.size, totalHosts.size)
        val rootHosts = visibleHosts.filter { it.parentId.isBlank() || it.parentId == "0" || it.parentId !in folderIds }
        var row = 0
        if (rootHosts.isNotEmpty() || filterText.isBlank() && folders.isNotEmpty()) {
            addGroup(I18n.getString("termora.welcome.my-hosts"), "0", null, rootHosts, row++)
        }
        for (folder in folders) {
            val hosts = visibleHosts.filter { it.parentId == folder.id }
            if (hosts.isNotEmpty() || filterText.isBlank()) addGroup(folder.name, folder.id, folder.ownerId, hosts, row++)
        }
        if (visibleHosts.isEmpty()) {
            val empty = JPanel(GridBagLayout()).apply {
                isOpaque = false
                border = BorderFactory.createEmptyBorder(UIScale.scale(84), 0, UIScale.scale(84), 0)
            }
            val titleKey = if (filterText.isBlank()) "termora.welcome.no-hosts" else "termora.welcome.no-results"
            val hintKey = if (filterText.isBlank()) "termora.welcome.empty-hint" else "termora.welcome.no-results-hint"
            val stack = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(EmptyStateIcon())
                add(Box.createVerticalStrut(UIScale.scale(18)))
                add(JLabel(I18n.getString(titleKey), SwingConstants.CENTER).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    font = font.deriveFont(Font.BOLD, UIScale.scale(18f))
                    foreground = HostViewStyle.foreground
                })
                add(Box.createVerticalStrut(UIScale.scale(8)))
                add(JLabel(I18n.getString(hintKey), SwingConstants.CENTER).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    font = font.deriveFont(UIScale.scale(13f))
                    foreground = HostViewStyle.secondary
                })
            }
            empty.add(stack)
            contentPanel.add(empty, rowConstraints(row++))
        }
        contentPanel.add(Box.createGlue(), rowConstraints(row).apply { weighty = 1.0; fill = GridBagConstraints.BOTH })
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun addGroup(title: String, parentId: String, ownerId: String?, hosts: List<Host>, row: Int) {
        val group = JPanel(BorderLayout(0, UIScale.scale(12))).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(if (row == 0) 0 else UIScale.scale(24), 0, 0, 0)
        }
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        header.add(JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, UIScale.scale(14f))
            foreground = HostViewStyle.foreground
        })
        header.add(JLabel(hosts.size.toString()).apply {
            font = font.deriveFont(UIScale.scale(12f))
            foreground = HostViewStyle.secondary
            border = BorderFactory.createEmptyBorder(0, UIScale.scale(10), 0, 0)
        })
        group.add(header, BorderLayout.NORTH)
        val cards = HostGroupPanel(parentId, ownerId)
        hosts.sortedBy { it.sort }.forEach { host ->
            val card = HostCard(host)
            hostCards.add(card)
            cards.add(card)
        }
        group.add(cards, BorderLayout.CENTER)
        contentPanel.add(group, rowConstraints(row))
    }

    private inner class HostGroupPanel(
        val parentId: String,
        private val ownerId: String?,
    ) : JPanel(HostCardGridLayout { scrollPane.viewport.extentSize.width }) {
        init {
            isOpaque = false
        }

        fun accepts(host: Host): Boolean = ownerId == null || ownerId == host.ownerId

        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            if (componentCount == 0 && filterText.isBlank()) size.height = UIScale.scale(32)
            return size
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (dropGroup !== this || dropCard != null) return
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = HostViewStyle.accent
                g2.stroke = BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val y = if (componentCount == 0) height / 2 else (height - UIScale.scale(2)).coerceAtLeast(1)
                g2.drawLine(UIScale.scale(8), y, (width - UIScale.scale(8)).coerceAtLeast(UIScale.scale(8)), y)
            } finally {
                g2.dispose()
            }
        }
    }

    private fun startCardDrag(card: HostCard, anchor: Point) {
        if (filterText.isNotBlank()) return
        val layeredPane = card.rootPane?.layeredPane ?: return
        if (card.width <= 0 || card.height <= 0) return

        card.pressed = false
        val snapshot = BufferedImage(card.width, card.height, BufferedImage.TYPE_INT_ARGB)
        val snapshotGraphics = snapshot.createGraphics()
        try {
            card.printAll(snapshotGraphics)
        } finally {
            snapshotGraphics.dispose()
        }

        draggingCard = card
        card.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        dragGhost = DragGhost(snapshot, layeredPane, Point(anchor)).also {
            layeredPane.add(it, JLayeredPane.DRAG_LAYER)
            layeredPane.repaint()
        }
        card.repaint()
    }

    private fun updateCardDrag(source: HostCard, point: Point) {
        if (draggingCard !== source) return
        dragGhost?.follow(source, point)
        val contentPoint = SwingUtilities.convertPoint(source, point, contentPanel)
        val deepest = SwingUtilities.getDeepestComponentAt(contentPanel, contentPoint.x, contentPoint.y)
        val hoveredCard = ancestorOfType<HostCard>(deepest)
        val group = (hoveredCard?.parent as? HostGroupPanel) ?: ancestorOfType<HostGroupPanel>(deepest)
        if (group == null || !group.accepts(source.host)) {
            clearDropTarget()
            return
        }

        val cards = group.components.filterIsInstance<HostCard>()
        val rawIndex: Int
        var after = false
        if (hoveredCard != null) {
            val cardIndex = cards.indexOf(hoveredCard)
            if (cardIndex < 0) {
                clearDropTarget()
                return
            }
            val cardPoint = SwingUtilities.convertPoint(contentPanel, contentPoint, hoveredCard)
            after = cardPoint.x >= hoveredCard.width / 2
            rawIndex = cardIndex + if (after) 1 else 0
        } else {
            rawIndex = cards.size
        }

        val sourceGroup = source.parent as? HostGroupPanel
        val sourceIndex = sourceGroup?.components?.filterIsInstance<HostCard>()?.indexOf(source) ?: -1
        val targetIndex = if (sourceGroup === group && sourceIndex >= 0 && rawIndex > sourceIndex) rawIndex - 1 else rawIndex
        if (sourceGroup === group && targetIndex == sourceIndex) {
            clearDropTarget()
            return
        }
        setDropTarget(group, hoveredCard, targetIndex, after)
    }

    private fun finishCardDrag(source: HostCard) {
        val group = dropGroup
        val targetIndex = dropIndex
        clearDropTarget()
        draggingCard = null
        releaseDragGhost()
        source.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        source.repaint()
        if (group == null || targetIndex < 0) return

        val ownerIds = accountManager.getOwnerIds()
        val all = hostManager.hosts().filter { it.ownerId in ownerIds && !it.isTemporary }
        val folderIds = all.filter { it.isFolder }.mapTo(mutableSetOf()) { it.id }
        cardDropUpdates(all, source.host.id, group.parentId, targetIndex, folderIds).forEach(hostManager::addHost)
    }

    private fun releaseDragGhost() {
        val ghost = dragGhost ?: return
        dragGhost = null
        ghost.release()
    }

    private fun removeDragGhost() {
        val ghost = dragGhost ?: return
        dragGhost = null
        ghost.disposeGhost()
    }

    private inner class DragGhost(
        private val image: BufferedImage,
        private val layeredPane: JLayeredPane,
        private val anchor: Point,
    ) : JComponent() {
        private val padding = UIScale.scale(20)
        private var lift = 0f
        private var opacity = 1f
        private var animation: Timer? = null

        init {
            isOpaque = false
            isFocusable = false
            setSize(image.width + padding * 2, image.height + padding * 2)
            animateLift()
        }

        fun follow(source: JComponent, point: Point) {
            val cursor = SwingUtilities.convertPoint(source, point, layeredPane)
            setLocation(cursor.x - anchor.x - padding, cursor.y - anchor.y - padding)
            repaint()
        }

        private fun animateLift() {
            val startedAt = System.nanoTime()
            animation = Timer(16, null).apply {
                addActionListener {
                    val progress = ((System.nanoTime() - startedAt) / 140_000_000f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
                    lift = eased
                    repaint()
                    if (progress >= 1f) stop()
                }
                start()
            }
        }

        fun release() {
            animation?.stop()
            val fromLift = lift
            val startedAt = System.nanoTime()
            animation = Timer(16, null).apply {
                addActionListener {
                    val progress = ((System.nanoTime() - startedAt) / 120_000_000f).coerceIn(0f, 1f)
                    val eased = progress * progress
                    lift = fromLift * (1f - eased)
                    opacity = 1f - progress
                    repaint()
                    if (progress >= 1f) {
                        stop()
                        disposeGhost()
                    }
                }
                start()
            }
        }

        fun disposeGhost() {
            animation?.stop()
            animation = null
            parent?.remove(this)
            layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

                val scale = 1f + 0.045f * lift
                val imageWidth = (image.width * scale).toInt()
                val imageHeight = (image.height * scale).toInt()
                val imageX = padding + (image.width - imageWidth) / 2
                val imageY = padding + (image.height - imageHeight) / 2 - (UIScale.scale(3) * lift).toInt()
                val arc = UIScale.scale(14)
                val shadowOffset = UIScale.scale(6)

                for (spread in UIScale.scale(10) downTo UIScale.scale(2) step UIScale.scale(2).coerceAtLeast(1)) {
                    val strength = (1f - spread / UIScale.scale(12f)).coerceIn(0.05f, 0.55f)
                    g2.composite = AlphaComposite.SrcOver.derive(opacity * lift * strength * 0.22f)
                    g2.color = Color.BLACK
                    g2.fillRoundRect(
                        imageX - spread,
                        imageY + shadowOffset - spread / 2,
                        imageWidth + spread * 2,
                        imageHeight + spread,
                        arc + spread,
                        arc + spread,
                    )
                }

                g2.composite = AlphaComposite.SrcOver.derive(opacity)
                g2.drawImage(image, imageX, imageY, imageWidth, imageHeight, null)
            } finally {
                g2.dispose()
            }
        }
    }

    private fun setDropTarget(group: HostGroupPanel, card: HostCard?, index: Int, after: Boolean) {
        if (dropGroup === group && dropCard === card && dropIndex == index && dropAfter == after) return
        clearDropTarget()
        dropGroup = group
        dropCard = card
        dropIndex = index
        dropAfter = after
        if (card != null) card.dropEdge = if (after) 1 else -1
        group.repaint()
    }

    private fun clearDropTarget() {
        dropCard?.let {
            it.dropEdge = 0
            it.repaint()
        }
        dropGroup?.repaint()
        dropGroup = null
        dropCard = null
        dropIndex = -1
        dropAfter = false
    }

    private inline fun <reified T : Component> ancestorOfType(component: Component?): T? {
        var current = component
        while (current != null) {
            if (current is T) return current
            current = current.parent
        }
        return null
    }

    private fun rowConstraints(row: Int) = GridBagConstraints().apply {
        gridx = 0; gridy = row; weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
    }

    private fun getHostIcon(host: Host): Icon {
        val os = host.options.extras["osIcon"]?.let { name -> OSDetector.OSType.entries.find { it.name == name } }
        val base = os?.getIcon() ?: ProtocolProvider.valueOf(host.protocol)?.getIcon() ?: Icons.terminal
        if (base !is DynamicIcon) return base
        return (if (FlatLaf.isLafDark()) base.dark else base).derive(22, 22)
    }

    private inner class HostCard(val host: Host) : JPanel(BorderLayout(UIScale.scale(12), 0)) {
        private var hover = 0f
        private var mouseInside = false
        var pressed = false
        var dropEdge = 0
        private var loading = false
        private var pressPoint: Point? = null
        private var hoverTimer: Timer? = null
        private var loadingTimer: Timer? = null
        private lateinit var moreButton: FlatButton

        init {
            isOpaque = false
            isFocusable = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(UIScale.scale(14), UIScale.scale(16), UIScale.scale(14), UIScale.scale(12))
            foreground = HostViewStyle.foreground
            getAccessibleContext().accessibleName = "${host.name}, ${hostCardAddress(host)}"
            toolTipText = listOf(host.name, hostCardAddress(host), host.remark).filter { it.isNotBlank() }.joinToString(" · ")
            add(object : JPanel(GridBagLayout()) {
                init {
                    isOpaque = false
                    preferredSize = UIScale.scale(Dimension(34, 34))
                    add(JLabel(getHostIcon(host)))
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.color = HostViewStyle.accentSurface
                        g2.fillRoundRect(0, 0, width, height, UIScale.scale(10), UIScale.scale(10))
                    } finally {
                        g2.dispose()
                    }
                    super.paintComponent(g)
                }
            }, BorderLayout.WEST)
            val details = JPanel(GridBagLayout()).apply { isOpaque = false }
            fun addLine(text: String, row: Int, size: Float, bold: Boolean, color: Color) {
                val label = JLabel(text).apply {
                    font = font.deriveFont(if (bold) Font.BOLD else Font.PLAIN, UIScale.scale(size))
                    foreground = color
                    minimumSize = Dimension(0, preferredSize.height)
                }
                details.add(label, GridBagConstraints().apply {
                    gridx = 0; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(if (row == 0) 0 else UIScale.scale(4), 0, 0, 0)
                })
            }
            addLine(host.name, 0, 14f, true, HostViewStyle.foreground)
            addLine(hostCardAddress(host), 1, 12f, false, HostViewStyle.secondary)
            add(details, BorderLayout.CENTER)
            moreButton = FlatButton().apply {
                icon = Icons.moreHorizontal
                buttonType = FlatButton.ButtonType.toolBarButton
                toolTipText = I18n.getString("termora.welcome.host-actions")
                accessibleContext.accessibleName = "$toolTipText: ${host.name}"
                preferredSize = UIScale.scale(Dimension(26, 26))
                isVisible = false
                addActionListener { showMenu(this, 0, height) }
            }
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                preferredSize = UIScale.scale(Dimension(26, 26))
                add(moreButton, BorderLayout.NORTH)
            }, BorderLayout.EAST)
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) {
                    scrollRectToVisible(Rectangle(0, 0, width, height))
                    updateActionsVisibility()
                    repaint()
                }
                override fun focusLost(e: FocusEvent) {
                    updateActionsVisibility()
                    repaint()
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    mouseInside = true
                    updateActionsVisibility()
                    animateHover(1f)
                }
                override fun mouseExited(e: MouseEvent) {
                    mouseInside = false
                    if (draggingCard !== this@HostCard) pressed = false
                    updateActionsVisibility()
                    animateHover(0f)
                }
                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger) { showMenu(this@HostCard, e.x, e.y); return }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressed = true
                        pressPoint = e.point
                        requestFocusInWindow()
                        repaint()
                    } else if (SwingUtilities.isMiddleMouseButton(e)) {
                        requestFocusInWindow()
                        open(e, selected = false)
                    }
                }
                override fun mouseReleased(e: MouseEvent) {
                    val wasDragging = draggingCard === this@HostCard
                    if (wasDragging) finishCardDrag(this@HostCard)
                    else if (SwingUtilities.isLeftMouseButton(e) && pressPoint != null && contains(e.point)) open(e)
                    pressPoint = null
                    pressed = false
                    repaint()
                    if (e.isPopupTrigger) showMenu(this@HostCard, e.x, e.y)
                }
            })
            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    if (e.modifiersEx and InputEvent.BUTTON1_DOWN_MASK == 0) return
                    val start = pressPoint ?: return
                    if (draggingCard == null) {
                        val threshold = UIScale.scale(6)
                        if (start.distance(e.point) < threshold) return
                        startCardDrag(this@HostCard, start)
                    }
                    updateCardDrag(this@HostCard, e.point)
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> open(e)
                        KeyEvent.VK_LEFT -> moveFocus(-1)
                        KeyEvent.VK_RIGHT -> moveFocus(1)
                        KeyEvent.VK_UP -> moveVertically(-1)
                        KeyEvent.VK_DOWN -> moveVertically(1)
                        KeyEvent.VK_CONTEXT_MENU -> showMenu(this@HostCard, width / 2, height / 2)
                        KeyEvent.VK_F10 -> if (e.isShiftDown) showMenu(this@HostCard, width / 2, height / 2) else return
                        else -> return
                    }
                    e.consume()
                }
            })
        }

        private fun updateActionsVisibility() {
            moreButton.isVisible = mouseInside || hasFocus()
        }

        private fun moveFocus(offset: Int) {
            hostCards.getOrNull(hostCards.indexOf(this) + offset)?.requestFocusInWindow()
        }

        private fun moveVertically(direction: Int) {
            val group = parent ?: return
            val columns = (group.layout as? HostCardGridLayout)?.columns(group.width) ?: 1
            val siblings = group.components.filterIsInstance<HostCard>()
            val index = siblings.indexOf(this)
            val target = siblings.getOrNull(index + direction * columns)
            if (target != null) {
                target.requestFocusInWindow()
                return
            }
            val edge = if (direction > 0) siblings.last() else siblings.first()
            val adjacent = hostCards.getOrNull(hostCards.indexOf(edge) + direction) ?: return
            val adjacentCards = adjacent.parent.components.filterIsInstance<HostCard>()
            val row = if (direction > 0) 0 else (adjacentCards.lastIndex / columns) * columns
            adjacentCards[(row + index % columns).coerceAtMost(adjacentCards.lastIndex)].requestFocusInWindow()
        }

        private fun showMenu(component: JComponent, x: Int, y: Int) {
            hostTreeProvider()?.showContextmenuForHost(host, component, x, y)
        }

        fun open(event: InputEvent, selected: Boolean = true) {
            if (loading) return
            val action = actionManager.getAction(OpenHostAction.OPEN_HOST) ?: return
            loading = true
            repaint()
            action.actionPerformed(OpenHostActionEvent(this, host, event, selected = selected))
            loadingTimer = Timer(3000) { loading = false; repaint() }.apply { isRepeats = false; start() }
        }

        private fun animateHover(target: Float) {
            hoverTimer?.stop()
            val from = hover
            val start = System.nanoTime()
            hoverTimer = Timer(16, null).apply {
                addActionListener {
                    val progress = ((System.nanoTime() - start) / 160_000_000f).coerceIn(0f, 1f)
                    val eased = 1f - (1f - progress) * (1f - progress)
                    hover = from + (target - from) * eased
                    if (progress == 1f) stop()
                    repaint()
                }
                start()
            }
        }

        override fun paint(g: Graphics) {
            if (draggingCard !== this) {
                super.paint(g)
                return
            }
            val g2 = g.create() as Graphics2D
            try {
                g2.composite = AlphaComposite.SrcOver.derive(0.28f)
                super.paint(g2)
            } finally {
                g2.dispose()
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = UIScale.scale(AppUi.radiusLarge)
                val base = HostViewStyle.surface
                val target = HostViewStyle.hover
                g2.color = Color(
                    (base.red + (target.red - base.red) * hover).toInt(),
                    (base.green + (target.green - base.green) * hover).toInt(),
                    (base.blue + (target.blue - base.blue) * hover).toInt(),
                ).let { if (pressed) it.darker() else it }
                g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                g2.color = if (hasFocus() || loading) HostViewStyle.accent else HostViewStyle.border
                g2.composite = AlphaComposite.SrcOver.derive(if (hasFocus() || loading) 1f else 0.9f)
                g2.stroke = BasicStroke(UIScale.scale(if (hasFocus()) 1.5f else 1f))
                g2.drawRoundRect(1, 1, width - 3, height - 3, arc, arc)
                if (dropEdge != 0) {
                    g2.composite = AlphaComposite.SrcOver
                    g2.color = HostViewStyle.accent
                    g2.stroke = BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    val x = if (dropEdge < 0) UIScale.scale(2) else (width - UIScale.scale(3)).coerceAtLeast(1)
                    g2.drawLine(x, UIScale.scale(10), x, (height - UIScale.scale(10)).coerceAtLeast(UIScale.scale(10)))
                }
            } finally {
                g2.dispose()
            }
        }

        fun stopAnimations() {
            hoverTimer?.stop()
            loadingTimer?.stop()
            loading = false
            pressed = false
            pressPoint = null
            dropEdge = 0
            mouseInside = false
            hover = 0f
            if (::moreButton.isInitialized) moreButton.isVisible = false
        }

        override fun removeNotify() {
            stopAnimations()
            super.removeNotify()
        }
    }

    override fun dispose() {
        removeDragGhost()
        hostCards.forEach { it.stopAnimations() }
    }

    private class EmptyStateIcon : JComponent() {
        private val icon = (Icons.terminal as? DynamicIcon)?.derive(28, 28) ?: Icons.terminal

        init {
            isOpaque = false
            alignmentX = Component.CENTER_ALIGNMENT
            preferredSize = UIScale.scale(Dimension(56, 56))
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = HostViewStyle.accentSurface
                g2.fillRoundRect(0, 0, width, height, UIScale.scale(16), UIScale.scale(16))
                val x = (width - icon.iconWidth) / 2
                val y = (height - icon.iconHeight) / 2
                icon.paintIcon(this, g2, x, y)
            } finally {
                g2.dispose()
            }
        }
    }
}

internal fun hostCardAddress(host: Host): String = when {
    host.protocol.equals("SSH", ignoreCase = true) -> {
        val address = if (host.port > 0 && host.port != 22) {
            val hostname = if (':' in host.host && !host.host.startsWith('[')) "[${host.host}]" else host.host
            "$hostname:${host.port}"
        } else host.host
        if (host.username.isNotBlank()) "${host.username}@$address" else address
    }
    host.protocol == "Serial" -> host.options.serialComm.port
    else -> host.host
}

internal fun hostCardProtocol(host: Host): String = when {
    host.protocol.equals("SSH", ignoreCase = true) -> "SSH"
    host.protocol == "Serial" -> "Serial · ${host.options.serialComm.baudRate} baud"
    else -> host.protocol
}

internal fun cardDropUpdates(
    allHosts: List<Host>,
    draggedId: String,
    targetParentId: String,
    targetIndex: Int,
    folderIds: Set<String> = allHosts.filter { it.isFolder }.mapTo(mutableSetOf()) { it.id },
): List<Host> {
    if (targetParentId != "0" && targetParentId !in folderIds) return emptyList()
    val hosts = allHosts.filterNot { it.isFolder }
    val dragged = hosts.firstOrNull { it.id == draggedId } ?: return emptyList()
    fun parentKey(host: Host): String = if (host.parentId in folderIds) host.parentId else "0"

    val sourceParentId = parentKey(dragged)
    val source = hosts.filter { parentKey(it) == sourceParentId }.sortedBy { it.sort }
    val target = if (sourceParentId == targetParentId) source else hosts.filter { parentKey(it) == targetParentId }.sortedBy { it.sort }
    val updates = linkedMapOf<String, Host>()
    val originalById = hosts.associateBy { it.id }

    fun record(host: Host, sort: Long, parentId: String = host.parentId) {
        val original = originalById[host.id] ?: host
        if (original.sort != sort || original.parentId != parentId) {
            updates[host.id] = original.copy(sort = sort, parentId = parentId)
        }
    }

    if (sourceParentId == targetParentId) {
        val ordered = source.filterNot { it.id == draggedId }.toMutableList()
        ordered.add(targetIndex.coerceIn(0, ordered.size), dragged)
        ordered.forEachIndexed { index, host -> record(host, index.toLong()) }
    } else {
        source.filterNot { it.id == draggedId }.forEachIndexed { index, host -> record(host, index.toLong()) }
        val orderedTarget = target.filterNot { it.id == draggedId }.toMutableList()
        val moved = dragged.copy(parentId = targetParentId)
        orderedTarget.add(targetIndex.coerceIn(0, orderedTarget.size), moved)
        orderedTarget.forEachIndexed { index, host -> record(host, index.toLong(), host.parentId) }
    }
    return updates.values.toList()
}
