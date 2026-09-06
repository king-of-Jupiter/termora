package app.termora

import java.awt.Color
import java.awt.Insets
import javax.swing.UIManager

/**
 * Shared application chrome tokens.
 *
 * Terminal palettes stay independent; these values only shape the desktop UI around them.
 */
internal object AppUi {
    const val radius = 10
    const val radiusLarge = 14

    val background = DynamicColor(Color(0xF5F7FA), Color(0x11141A))
    val surface = DynamicColor(Color(0xFFFFFF), Color(0x1A1E26))
    val surfaceSoft = DynamicColor(Color(0xF0F3F7), Color(0x20252E))
    val hover = DynamicColor(Color(0xE9EEF5), Color(0x272D38))
    val pressed = DynamicColor(Color(0xE1E7EF), Color(0x303744))
    val border = DynamicColor(Color(0xDCE2EA), Color(0x303744))
    val foreground = DynamicColor(Color(0x1D2633), Color(0xF2F4F7))
    val secondary = DynamicColor(Color(0x687588), Color(0x98A2B3))
    val accent = DynamicColor(Color(0x3768D9), Color(0x4F75E6))
    val accentHover = DynamicColor(Color(0x2E5FCB), Color(0x5C82F0))
    val accentSurface = DynamicColor(Color(0xE8EEFC), Color(0x243052))
    val accentSurfaceHover = DynamicColor(Color(0xDCE6FA), Color(0x2C3B63))

    fun install() {
        UIManager.put("window", background)
        UIManager.put("Dialog.background", background)
        UIManager.put("Panel.background", background)
        UIManager.put("Viewport.background", background)
        UIManager.put("ScrollPane.background", background)
        UIManager.put("ToolBar.background", background)

        UIManager.put("TitlePane.background", surface)
        UIManager.put("TitlePane.inactiveBackground", surface)
        UIManager.put("TitlePane.foreground", foreground)
        UIManager.put("TitlePane.inactiveForeground", secondary)
        UIManager.put("TitlePane.buttonHoverBackground", hover)
        UIManager.put("TitlePane.buttonPressedBackground", pressed)

        UIManager.put("Component.arc", radius)
        UIManager.put("Button.arc", radius)
        UIManager.put("TextComponent.arc", radius)
        UIManager.put("CheckBox.arc", 4)
        UIManager.put("Component.focusWidth", 1)
        UIManager.put("Component.innerFocusWidth", 0)
        UIManager.put("Component.hideMnemonics", true)
        UIManager.put("Component.borderColor", border)
        UIManager.put("Component.disabledBorderColor", border)
        UIManager.put("Component.focusColor", accent)
        UIManager.put("Component.focusedBorderColor", accent)

        UIManager.put("TextField.background", surface)
        UIManager.put("PasswordField.background", surface)
        UIManager.put("FormattedTextField.background", surface)
        UIManager.put("TextArea.background", surface)
        UIManager.put("TextPane.background", surface)
        UIManager.put("EditorPane.background", surface)
        UIManager.put("ComboBox.background", surface)
        UIManager.put("Spinner.background", surface)
        UIManager.put("Spinner.buttonBackground", surfaceSoft)
        UIManager.put("Spinner.buttonSeparatorColor", border)
        UIManager.put("Spinner.buttonDisabledSeparatorColor", border)
        UIManager.put("TextField.placeholderForeground", secondary)
        UIManager.put("TextField.margin", Insets(7, 10, 7, 10))
        UIManager.put("PasswordField.margin", Insets(7, 10, 7, 10))
        UIManager.put("FormattedTextField.margin", Insets(7, 10, 7, 10))
        UIManager.put("TextArea.margin", Insets(7, 10, 7, 10))
        UIManager.put("TextPane.margin", Insets(7, 10, 7, 10))
        UIManager.put("EditorPane.margin", Insets(7, 10, 7, 10))
        UIManager.put("ComboBox.padding", Insets(5, 10, 5, 10))

        UIManager.put("Button.background", surface)
        UIManager.put("Button.hoverBackground", hover)
        UIManager.put("Button.pressedBackground", pressed)
        UIManager.put("Button.default.background", accent)
        UIManager.put("Button.default.foreground", Color.WHITE)
        UIManager.put("Button.default.hoverBackground", accentHover)
        UIManager.put("Button.default.focusedBorderColor", accent)

        UIManager.put("ToggleButton.background", surface)
        UIManager.put("ToggleButton.hoverBackground", hover)
        UIManager.put("ToggleButton.pressedBackground", pressed)
        UIManager.put("ToggleButton.selectedBackground", accentSurface)
        UIManager.put("ToggleButton.selectedForeground", accent)
        UIManager.put("ToggleButton.selectedHoverBackground", accentSurfaceHover)
        UIManager.put("ToggleButton.selectedPressedBackground", accentSurfaceHover)
        UIManager.put("ToggleButton.toolbar.hoverBackground", hover)
        UIManager.put("ToggleButton.toolbar.pressedBackground", pressed)
        UIManager.put("ToggleButton.toolbar.selectedBackground", accentSurface)
        UIManager.put("ToggleButton.toolbar.selectedForeground", accent)
        UIManager.put("ToggleButton.toolbar.disabledSelectedBackground", accentSurface)

        UIManager.put("PopupMenu.background", surface)
        UIManager.put("Menu.background", surface)
        UIManager.put("Menu.foreground", foreground)
        UIManager.put("Menu.selectionBackground", hover)
        UIManager.put("Menu.selectionForeground", foreground)
        UIManager.put("MenuItem.background", surface)
        UIManager.put("MenuItem.foreground", foreground)
        UIManager.put("MenuItem.selectionBackground", hover)
        UIManager.put("MenuItem.selectionForeground", foreground)
        UIManager.put("CheckBoxMenuItem.selectionBackground", hover)
        UIManager.put("CheckBoxMenuItem.selectionForeground", foreground)
        UIManager.put("RadioButtonMenuItem.selectionBackground", hover)
        UIManager.put("RadioButtonMenuItem.selectionForeground", foreground)
        UIManager.put("ToolTip.background", surface)
        UIManager.put("ToolTip.foreground", foreground)
        UIManager.put("Separator.foreground", border)
        UIManager.put("Separator.height", 1)
        UIManager.put("Separator.stripeWidth", 1)

        UIManager.put("Table.background", surface)
        UIManager.put("Table.alternateRowColor", surfaceSoft)
        UIManager.put("Table.gridColor", border)
        UIManager.put("Table.selectionBackground", accentSurface)
        UIManager.put("Table.selectionForeground", foreground)
        UIManager.put("Table.rowHeight", 30)
        UIManager.put("TableHeader.background", surfaceSoft)
        UIManager.put("TableHeader.foreground", secondary)
        UIManager.put("TableHeader.separatorColor", border)
        UIManager.put("TableHeader.height", 32)

        UIManager.put("Tree.background", background)
        UIManager.put("Tree.selectionBackground", accentSurface)
        UIManager.put("Tree.selectionForeground", foreground)
        UIManager.put("Tree.rowHeight", 30)
        UIManager.put("List.background", surface)
        UIManager.put("List.selectionBackground", accentSurface)
        UIManager.put("List.selectionForeground", foreground)
        UIManager.put("List.selectionArc", radius)

        UIManager.put("TabbedPane.tabSelectionHeight", 2)
        UIManager.put("TabbedPane.underlineColor", accent)
        UIManager.put("TabbedPane.inactiveUnderlineColor", border)
        UIManager.put("TabbedPane.hoverColor", hover)
        UIManager.put("TabbedPane.focusColor", hover)

        UIManager.put("ScrollBar.width", 10)
        UIManager.put("ScrollBar.thumbArc", 999)
        UIManager.put("ScrollBar.trackArc", 999)
        UIManager.put("ScrollBar.thumbInsets", Insets(2, 2, 2, 2))
        UIManager.put("ScrollBar.trackInsets", Insets(2, 2, 2, 2))
        UIManager.put("ProgressBar.arc", 999)
    }
}
