package app.termora

import app.termora.actions.StateAction
import app.termora.findeverywhere.FindEverywhereAction
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.util.SystemInfo
import com.formdev.flatlaf.util.UIScale
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*

internal class MyTermoraToolbar(private val windowScope: WindowScope, private val frame: TermoraFrame) : FlatToolBar() {


    private val customizeToolBarAWTEventListener = CustomizeToolBarAWTEventListener()
    private val model get() = TermoraToolbarModel.getInstance()
    private val actionManager get() = model.getActionManager()
    private val toolbar get() = this

    /**
     * 一次性生命周期 每次刷新都会重置
     */
    private var disposable = Disposer.newDisposable()

    init {
        initView()
        initEvents()
        refreshActions()
    }

    private fun initView() {
        isFloatable = false
        isOpaque = true
        background = AppUi.surfaceSoft
        border = BorderFactory.createEmptyBorder(
            UIScale.scale(4), UIScale.scale(5), UIScale.scale(4), UIScale.scale(6)
        )
    }

    private fun initEvents() {
        Disposer.register(windowScope, object : Disposable {
            override fun dispose() {
                Disposer.dispose(disposable)
            }
        })


        // 监听全局事件
        toolkit.addAWTEventListener(customizeToolBarAWTEventListener, AWTEvent.MOUSE_EVENT_MASK)
        Disposer.register(windowScope, customizeToolBarAWTEventListener)

        // 监听变化
        model.addTermoraToolbarModelListener(object : TermoraToolbarModel.TermoraToolbarModelListener {
            override fun onChanged() {
                refreshActions()
            }
        }).let { Disposer.register(windowScope, it) }

        // 监听窗口大小变动，然后修改边距避开控制按钮
        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    adjust()
                }
            })
        }
    }

    private fun refreshActions() {
        Disposer.dispose(disposable)
        disposable = Disposer.newDisposable()

        removeAll()

        add(JButton(object : AbstractAction() {
            init {
                putValue(SMALL_ICON, Icons.add)
            }

            override fun actionPerformed(evt: ActionEvent) {
                actionManager.getAction(FindEverywhereAction.FIND_EVERYWHERE)?.actionPerformed(evt)
            }
        }).also {
            it.toolTipText = I18n.getString("termora.find-everywhere")
            styleToolbarButton(it, accent = true)
        })

        if (SystemInfo.isLinux || SystemInfo.isWindows) {
            add(Box.createHorizontalStrut(UIScale.scale(8)))
        }

        add(Box.createHorizontalGlue())

        var visibleActionCount = 0
        for (action in model.getActions()) {
            if (action.visible.not()) continue
            val action = actionManager.getAction(action.id) ?: continue
            if (visibleActionCount > 0) {
                add(Box.createHorizontalStrut(UIScale.scale(2)))
            }
            add(redirectAction(action, disposable))
            visibleActionCount++
        }

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            adjust()
        }

        revalidate()
        repaint()
    }

    private fun adjust() {
        val rectangle = frame.rootPane.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS)
                as? Rectangle ?: return
        val right = rectangle.width

        for (i in 0 until toolbar.componentCount) {
            val c = toolbar.getComponent(i)
            if (c.name == "spacing") {
                if (c.width == right) {
                    return
                }
                toolbar.remove(i)
                break
            }
        }

        val spacing = Box.createHorizontalStrut(right)
        spacing.name = "spacing"
        toolbar.add(spacing)
    }

    private fun redirectAction(action: Action, disposable: Disposable): AbstractButton {
        val button = if (action is StateAction) JToggleButton() else JButton()
        button.toolTipText = (action.getValue(Action.SHORT_DESCRIPTION) as? String)
            ?: action.getValue(Action.NAME) as? String
        button.icon = action.getValue(Action.SMALL_ICON) as? Icon
        styleToolbarButton(button)
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                action.actionPerformed(e)
                if (action is StateAction) {
                    button.isSelected = action.isSelected(windowScope)
                }
            }
        })

        val listener = object : PropertyChangeListener, Disposable {
            override fun propertyChange(evt: PropertyChangeEvent) {
                if (action is StateAction) {
                    button.isSelected = action.isSelected(windowScope)
                }
                button.icon = action.getValue(Action.SMALL_ICON) as? Icon
            }

            override fun dispose() {
                action.removePropertyChangeListener(this)
            }
        }

        action.addPropertyChangeListener(listener)
        Disposer.register(disposable, listener)

        return button
    }

    private fun styleToolbarButton(button: AbstractButton, accent: Boolean = false) {
        val size = UIScale.scale(Dimension(30, 30))
        button.preferredSize = size
        button.minimumSize = size
        button.maximumSize = size
        button.margin = Insets(0, 0, 0, 0)
        button.isOpaque = false
        button.putClientProperty(
            FlatClientProperties.STYLE,
            mapOf(
                "arc" to AppUi.radius - 2,
                "background" to if (accent) AppUi.accentSurface else AppUi.surfaceSoft,
                "hoverBackground" to if (accent) AppUi.accentSurfaceHover else AppUi.hover,
                "pressedBackground" to if (accent) AppUi.accentSurfaceHover else AppUi.pressed,
                "selectedBackground" to AppUi.accentSurface,
                "selectedForeground" to AppUi.accent,
                "foreground" to AppUi.foreground,
                "borderWidth" to 0,
                "focusWidth" to 0,
                "innerFocusWidth" to 0,
                "margin" to Insets(0, 0, 0, 0),
            )
        )
    }

    /**
     * 对着 ToolBar 右键
     */
    private inner class CustomizeToolBarAWTEventListener : AWTEventListener, Disposable {
        override fun eventDispatched(event: AWTEvent) {
            if (event !is MouseEvent || event.id != MouseEvent.MOUSE_CLICKED
                || SwingUtilities.isRightMouseButton(event).not()
            ) return

            // 如果 ToolBar 没有显示
            if (toolbar.isShowing.not()) return

            // 如果不是作用于在 ToolBar 上面
            if (Rectangle(toolbar.locationOnScreen, toolbar.size).contains(event.locationOnScreen).not()) return

            // 显示右键菜单
            showContextMenu(event)
        }

        private fun showContextMenu(event: MouseEvent) {
            val popupMenu = FlatPopupMenu()
            popupMenu.add(I18n.getString("termora.toolbar.customize-toolbar")).addActionListener {
                val owner = windowScope.window
                val dialog = CustomizeToolBarDialog(owner, windowScope, model)
                dialog.setLocationRelativeTo(owner)
                if (dialog.open()) {
                    model.setActions(dialog.getActions())
                }
            }
            popupMenu.show(event.component, event.x, event.y)
        }

        override fun dispose() {
            toolkit.removeAWTEventListener(this)
        }
    }
}
