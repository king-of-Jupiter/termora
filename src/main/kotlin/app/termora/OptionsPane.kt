package app.termora

import app.termora.plugin.internal.extension.DynamicExtensionHandler
import com.formdev.flatlaf.FlatLaf
import java.awt.*
import javax.swing.*
import javax.swing.border.Border


abstract class OptionsPane : JPanel(BorderLayout()), Disposable {
    companion object {
        const val FORM_MARGIN = "9dlu"
    }

    private val options = mutableListOf<Option>()
    protected val tabListModel = DefaultListModel<Option>()
    protected val tabList = JList<Option>(tabListModel)
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val loadedComponents = mutableMapOf<String, JComponent>()
    private var contentPanelBorder = BorderFactory.createEmptyBorder(18, 22, 16, 22)
    private var themeChanged = false

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        tabList.fixedCellHeight = 40
        tabList.fixedCellWidth = 176
        tabList.background = AppUi.surfaceSoft
        tabList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        tabList.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, AppUi.border),
            BorderFactory.createEmptyBorder(10, 8, 10, 8)
        )
        tabList.cellRenderer = object : DefaultListCellRenderer() {
            private var selected = false

            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val option = value as Option
                val c = super.getListCellRendererComponent(list, option.getTitle(), index, isSelected, cellHasFocus)
                selected = isSelected

                icon = option.getIcon(isSelected)
                if (isSelected && tabList.hasFocus()) {
                    if (!FlatLaf.isLafDark()) {
                        if (icon is DynamicIcon) {
                            icon = (icon as DynamicIcon).dark
                        }
                    }
                }

                isOpaque = false
                iconTextGap = 9
                if (isSelected) {
                    foreground = AppUi.accent
                } else {
                    foreground = AppUi.foreground
                }
                border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

                return c
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    if (selected) {
                        g2.color = AppUi.accentSurface
                        g2.fillRoundRect(2, 2, width - 4, height - 4, AppUi.radius, AppUi.radius)
                    }
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }


        add(tabList, BorderLayout.WEST)
        add(contentPanel, BorderLayout.CENTER)
    }

    fun selectOption(option: Option) {
        val index = tabListModel.indexOf(option)
        if (index < 0) {
            return
        }
        setSelectedIndex(index)
    }

    fun getSelectedOption(): Option? {
        val index = tabList.selectedIndex
        if (index < 0) return null
        return tabListModel.getElementAt(index)
    }

    fun getSelectedIndex(): Int {
        return tabList.selectedIndex
    }

    fun setSelectedIndex(index: Int) {
        tabList.selectedIndex = index
    }

    fun selectOptionJComponent(c: JComponent) {
        for (element in tabListModel.elements()) {
            var p = c as Container?
            while (p != null) {
                if (p == element) {
                    selectOption(element)
                    return
                }
                p = p.parent
            }
        }
    }


    open fun addOption(option: Option) {
        for (element in tabListModel.elements()) {
            if (element.getTitle() == option.getTitle()) {
                throw UnsupportedOperationException("Title already exists")
            }
        }

        options.add(option)

        tabListModel.clear()
        for (e in OptionSorter.sortOptions(options)) {
            tabListModel.addElement(e)
        }

        if (tabList.selectedIndex < 0) {
            setSelectedIndex(0)
        }

        if (option is Disposable) {
            Disposer.register(this, option)
        }

    }

    fun setContentBorder(border: Border) {
        contentPanelBorder = border
        contentPanel.border = border
    }

    private fun initEvents() {
        tabList.addListSelectionListener {
            if (tabList.selectedIndex >= 0) {
                val option = tabListModel.get(tabList.selectedIndex)
                val title = option.getTitle()
                option.onSelected()

                if (!loadedComponents.containsKey(title)) {
                    val component = decorateOptionComponent(option, option.getJComponent())
                    loadedComponents[title] = component
                    contentPanel.add(component, title)
                    if (themeChanged) SwingUtilities.updateComponentTreeUI(component)
                }

                val contentPanelBorder = option.getJComponent().getClientProperty("ContentPanelBorder")
                if (contentPanelBorder is Border) {
                    contentPanel.border = contentPanelBorder
                } else {
                    contentPanel.border = this.contentPanelBorder
                }

                cardLayout.show(contentPanel, title)
            }
        }

        // 监听主题变化
        DynamicExtensionHandler.getInstance().register(ThemeChangeExtension::class.java, object : ThemeChangeExtension {
            override fun onChanged() {
                themeChanged = true
            }
        }).let { Disposer.register(this, it) }
    }

    /**
     * Allows specialized option panes to provide a page shell without changing the option itself.
     * The original component remains in the hierarchy, so option lifecycle and descendant lookup keep working.
     */
    protected open fun decorateOptionComponent(option: Option, component: JComponent): JComponent = component


    interface Option {
        fun getIcon(isSelected: Boolean): Icon
        fun getTitle(): String
        fun getJComponent(): JComponent
        fun getIdentifier(): String = javaClass.name
        fun getAnchor(): Anchor = Anchor.Null
        fun onSelected() {}
    }


    sealed class Anchor {
        object Null : Anchor()
        object First : Anchor()
        object Last : Anchor()
        data class Before(val target: String) : Anchor()
        data class After(val target: String) : Anchor()
    }

    private object OptionSorter {


        fun sortOptions(options: List<Option>): List<Option> {
            val firsts = options.filter { it.getAnchor() is Anchor.First }
            val lasts = options.filter { it.getAnchor() is Anchor.Last }
            val nulls = options.filter { it.getAnchor() is Anchor.Null }
            val pendingOptions = mutableListOf<Option>()

            val result = mutableListOf<Option>()
            result.addAll(firsts)
            result.addAll(nulls)
            result.addAll(lasts)

            // 首次排序
            sort(options, pendingOptions, result)

            // 对于没有找到对应依赖关系，则在最近的一个 Last 前面
            if (pendingOptions.isNotEmpty()) {
                for (i in 0 until result.size) {
                    if (result[i].getAnchor() is Anchor.Last || i == result.size - 1) {
                        for (n in 0 until pendingOptions.size) {
                            result.add(i + n, pendingOptions[n])
                        }
                        break
                    }
                }
            }


            return result
        }

        private fun sort(
            options: List<Option>,
            pendingOptions: MutableList<Option>,
            result: MutableList<Option>
        ) {
            for (option in options.filter { it.getAnchor() is Anchor.Before || it.getAnchor() is Anchor.After }) {
                val anchor = option.getAnchor()
                if (anchor is Anchor.Before) {
                    val index = findIndex(anchor.target, result)
                    if (index == -1) {
                        pendingOptions.add(option)
                        continue
                    } else {
                        result.add(index, option)
                    }
                } else if (anchor is Anchor.After) {
                    val index = findIndex(anchor.target, result)
                    if (index == -1) {
                        pendingOptions.add(option)
                        continue
                    } else {
                        result.add(index + 1, option)
                    }
                }
            }
        }

        private fun findIndex(identifier: String, list: List<Option>): Int {
            return list.indexOfFirst { it.getIdentifier() == identifier }
        }

    }
}
