package app.termora

import com.formdev.flatlaf.util.UIScale
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/** Every group uses the viewport width, including groups with only one host. */
internal class HostCardGridLayout(private val availableWidth: () -> Int) : LayoutManager {
    private val gap get() = UIScale.scale(12)
    private val cardHeight get() = UIScale.scale(80)
    private val minimumCardWidth get() = UIScale.scale(272)

    fun columns(width: Int): Int = ((width + gap) / (minimumCardWidth + gap)).coerceAtLeast(1)

    override fun preferredLayoutSize(parent: Container): Dimension {
        val insets = parent.insets
        val width = availableWidth().coerceAtLeast(1)
        val count = parent.components.count { it.isVisible }
        val columns = columns(width - insets.left - insets.right)
        val rows = (count + columns - 1) / columns
        val height = rows * cardHeight + (rows - 1).coerceAtLeast(0) * gap
        return Dimension(width, height + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, cardHeight)

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val width = (parent.width - insets.left - insets.right).coerceAtLeast(0)
        val columns = columns(width)
        val usableWidth = (width - gap * (columns - 1)).coerceAtLeast(0)
        parent.components.filter { it.isVisible }.forEachIndexed { index, component ->
            val column = index % columns
            val left = usableWidth * column / columns + gap * column
            val right = usableWidth * (column + 1) / columns + gap * column
            component.setBounds(
                insets.left + left,
                insets.top + (index / columns) * (cardHeight + gap),
                right - left,
                cardHeight,
            )
        }
    }

    override fun addLayoutComponent(name: String?, component: Component?) = Unit
    override fun removeLayoutComponent(component: Component?) = Unit
}
