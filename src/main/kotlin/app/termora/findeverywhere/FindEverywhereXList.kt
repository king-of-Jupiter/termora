package app.termora.findeverywhere

import app.termora.AppUi
import app.termora.Icons
import com.formdev.flatlaf.ui.FlatListUI
import org.jdesktop.swingx.JXList
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.*

class FindEverywhereXList(private val model: DefaultListModel<FindEverywhereResult>) : JXList(model) {

    init {
        initView()
    }

    override fun processMouseEvent(e: MouseEvent) {
        if (isGroup(e.point)) {
            return
        }
        super.processMouseEvent(e)
    }

    override fun processMouseMotionEvent(e: MouseEvent) {
        if (isGroup(e.point)) {
            return
        }
        super.processMouseMotionEvent(e)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (elementCount == 0) {
            paintEmptyText(g)
        }
    }

    private fun paintEmptyText(g: Graphics) {
        if (g !is Graphics2D) return
        g.setRenderingHints(
            RenderingHints(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )
        )

        // Keep the empty state aligned with the application's native icon system.
        val icon = Icons.find
        val iconY = (height * 0.22).toInt() - icon.iconHeight / 2
        icon.paintIcon(this, g, width / 2 - icon.iconWidth / 2, iconY)

        g.color = AppUi.secondary
        g.font = font.deriveFont(font.size2D)
        val text = app.termora.I18n.getString("termora.find-everywhere.nothing-found")
        val w = g.fontMetrics.stringWidth(text)
        g.drawString(text, width / 2 - w / 2, iconY + icon.iconHeight + 24)
    }

    private fun isGroup(e: Point): Boolean {
        val index = locationToIndex(e)
        if (index < 0) return false
        return model.getElementAt(index) is GroupFindEverywhereResult
    }


    private fun initView() {
        selectionModel = object : DefaultListSelectionModel() {
            override fun setSelectionInterval(index0: Int, index1: Int) {
                var index = index0
                if (model.get(index) is GroupFindEverywhereResult) {
                    val currentIndex = selectedIndex
                    if (index > currentIndex) {
                        index++
                    } else {
                        index--
                    }
                }

                super.setSelectionInterval(index, index)

                when (index) {
                    1 -> ensureIndexIsVisible(0)
                    model.size() - 1 -> ensureIndexIsVisible(model.size() - 1)
                    else -> ensureIndexIsVisible(index - 1)
                }
            }
        }

        setUI(FlatJXListUI())

        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {

                if (value is GroupFindEverywhereResult) {
                    val label = JLabel(value.toString())
                    label.foreground = AppUi.secondary
                    label.font = font.deriveFont(Font.BOLD, font.size2D - 2f)
                    val panel = JPanel(BorderLayout())
                    panel.background = AppUi.surface
                    panel.border = BorderFactory.createEmptyBorder(8, 10, 2, 10)
                    panel.add(label, BorderLayout.CENTER)
                    return panel
                }

                val c = super.getListCellRendererComponent(
                    list,
                    if (value is FindEverywhereResult) value.getText(isSelected) else value,
                    index,
                    isSelected,
                    cellHasFocus
                )
                border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
                iconTextGap = 10
                if (isSelected) {
                    background = AppUi.accentSurface
                    foreground = AppUi.foreground
                } else {
                    background = AppUi.surface
                    foreground = AppUi.foreground
                }
                if (value is FindEverywhereResult) {
                    icon = value.getIcon(isSelected)
                }
                return c
            }
        }

    }


    private class FlatJXListUI : FlatListUI() {
        override fun paintCell(
            g: Graphics,
            row: Int,
            rowBounds: Rectangle,
            cellRenderer: ListCellRenderer<*>,
            dataModel: ListModel<*>,
            selModel: ListSelectionModel,
            leadIndex: Int
        ) {
            if (cellRenderer is JXList.DelegatingRenderer) {
                super.paintCell(g, row, rowBounds, cellRenderer.delegateRenderer, dataModel, selModel, leadIndex)
            } else {
                super.paintCell(g, row, rowBounds, cellRenderer, dataModel, selModel, leadIndex)
            }

        }
    }
}
