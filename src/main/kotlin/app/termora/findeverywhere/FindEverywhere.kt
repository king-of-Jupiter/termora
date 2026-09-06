package app.termora.findeverywhere

import app.termora.DialogWrapper
import app.termora.AppUi
import app.termora.Icons
import app.termora.I18n
import app.termora.WindowScope
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.macro.MacroFindEverywhereProvider
import com.formdev.flatlaf.extras.components.FlatTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import java.awt.Window
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FindEverywhere(owner: Window, private val windowScope: WindowScope) : DialogWrapper(owner) {
    private val searchTextField = FlatTextField()
    private val model = DefaultListModel<FindEverywhereResult>()
    private val resultList = FindEverywhereXList(model)
    private val centerPanel = JPanel(BorderLayout())
    private val providers = mutableListOf<FindEverywhereProvider>(
        BasicFilterFindEverywhereProvider(QuickCommandFindEverywhereProvider()),
        BasicFilterFindEverywhereProvider(SettingsFindEverywhereProvider()),
        BasicFilterFindEverywhereProvider(QuickActionsFindEverywhereProvider(windowScope)),
        BasicFilterFindEverywhereProvider(MacroFindEverywhereProvider()),
    )


    init {
        initView()
        initEvents()
        init()
    }


    private fun initView() {

        size = Dimension(640, 520)
        minimumSize = Dimension(480, 360)
        isModal = false
        lostFocusDispose = true
        setLocationRelativeTo(null)

        centerPanel.background = AppUi.surface
        centerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppUi.border),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)
        )

        searchTextField.placeholderText = I18n.getString("termora.find-everywhere.search-for-something")
        searchTextField.accessibleContext.accessibleName = searchTextField.placeholderText
        searchTextField.leadingIcon = Icons.find
        searchTextField.background = AppUi.surfaceSoft
        searchTextField.preferredSize = Dimension(-1, 44)
        searchTextField.padding = Insets(0, 8, 0, 8)
        searchTextField.focusTraversalKeysEnabled = false

        resultList.isFocusable = false
        resultList.fixedCellHeight = 40
        resultList.isRolloverEnabled = false
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
        resultList.background = AppUi.surface


        val scrollPane = JScrollPane(resultList)
        scrollPane.verticalScrollBar.maximumSize = Dimension(0, 0)
        scrollPane.verticalScrollBar.preferredSize = Dimension(0, 0)
        scrollPane.verticalScrollBar.minimumSize = Dimension(0, 0)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.background = AppUi.surface
        scrollPane.viewport.background = AppUi.surface

        centerPanel.add(searchTextField, BorderLayout.NORTH)
        centerPanel.add(scrollPane, BorderLayout.CENTER)

    }

    private fun search() {
        model.clear()

        val text = searchTextField.text.trim()
        val map = linkedMapOf<String, MutableList<FindEverywhereResult>>()

        for (provider in providers) {
            val results = provider.find(text, windowScope)
            if (results.isEmpty()) {
                continue
            }
            map.getOrPut(provider.group()) { mutableListOf() }
                .addAll(results)
        }

        for (e in map.entries) {
            model.addElement(GroupFindEverywhereResult(e.key))
            model.addAll(e.value)
        }

        if (model.size() > 0) {
            resultList.selectedIndex = 0
        }
    }

    private fun initEvents() {

        // 搜索
        searchTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                changedUpdate(e)
            }

            override fun removeUpdate(e: DocumentEvent) {
                changedUpdate(e)
            }

            override fun changedUpdate(e: DocumentEvent) {
                search()
            }

        })

        // 箭头操作
        searchTextField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {


                var action: Action? = null

                when (e.keyCode) {
                    KeyEvent.VK_UP -> {
                        action = if (resultList.selectedIndex == 1) {
                            resultList.actionMap.get("selectLastRow")
                        } else {
                            resultList.actionMap.get("selectPreviousRow")
                        }
                    }

                    KeyEvent.VK_DOWN -> {
                        action =
                            if (resultList.selectedIndex + 1 == resultList.elementCount) {
                                object : AnAction() {
                                    override fun actionPerformed(evt: AnActionEvent) {
                                        resultList.selectedIndex = 1
                                    }
                                }
                            } else {
                                resultList.actionMap.get("selectNextRow")
                            }
                    }

                    KeyEvent.VK_ENTER -> {
                        action = resultList.actionMap.get("action")
                    }

                }

                action?.actionPerformed(ActionEvent(resultList, ActionEvent.ACTION_PERFORMED, String()))
            }
        })


        resultList.actionMap.put("action", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                if (resultList.selectedIndex < 0) {
                    return
                }

                val event = ActionEvent(evt.source, ActionEvent.ACTION_PERFORMED, String())

                // fire
                SwingUtilities.invokeLater { model.get(resultList.selectedIndex).actionPerformed(event) }

                // close
                doCancelAction()
            }
        })

        // 点击
        resultList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (resultList.locationToIndex(e.point) < 0) {
                        return
                    }
                    resultList.actionMap.get("action")
                        .actionPerformed(ActionEvent(e.source, ActionEvent.ACTION_PERFORMED, String()))
                }
            }
        })

    }

    fun registerProvider(provider: FindEverywhereProvider) {
        providers.add(provider)
        providers.sortBy { it.order() }
    }

    fun unregisterProvider(provider: FindEverywhereProvider) {
        providers.remove(provider)
    }

    override fun createCenterPanel(): JComponent {
        return centerPanel
    }

    override fun createTitlePanel(): JPanel? {
        return null
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            search()
        }
        super.setVisible(visible)
    }

    override fun addNotify() {
        super.addNotify()

        controlsVisible = false
        fullWindowContent = true
    }

}
