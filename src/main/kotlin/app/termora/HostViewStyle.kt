package app.termora

import java.awt.Color

/** Colors are local to the host workspace; terminal color schemes remain independent. */
internal object HostViewStyle {
    val background = DynamicColor(Color(0xF6F7F9), Color(0x17191F))
    val surface = DynamicColor(Color(0xFFFFFF), Color(0x20232B))
    val hover = DynamicColor(Color(0xF2F4F7), Color(0x272B34))
    val border = DynamicColor(Color(0xD9DEE6), Color(0x343944))
    val foreground = DynamicColor(Color(0x202630), Color(0xECEFF4))
    val secondary = DynamicColor(Color(0x687384), Color(0x949EAE))
    val accent = DynamicColor(Color(0x3768B2), Color(0x6F9FE8))
    val accentSurface = DynamicColor(Color(0xE8EFF9), Color(0x293A52))
    val accentSurfaceHover = DynamicColor(Color(0xDDE8F6), Color(0x314866))
}
