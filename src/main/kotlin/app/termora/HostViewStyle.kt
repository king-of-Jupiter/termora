package app.termora

/** Colors are local to the host workspace; terminal color schemes remain independent. */
internal object HostViewStyle {
    val background = AppUi.background
    val surface = AppUi.surface
    val hover = AppUi.hover
    val border = AppUi.border
    val foreground = AppUi.foreground
    val secondary = AppUi.secondary
    val accent = AppUi.accent
    val accentSurface = AppUi.accentSurface
    val accentSurfaceHover = AppUi.accentSurfaceHover
}
