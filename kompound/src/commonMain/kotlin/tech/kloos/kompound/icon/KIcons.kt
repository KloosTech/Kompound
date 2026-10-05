package tech.kloos.kompound.icon

import androidx.compose.ui.graphics.vector.ImageVector
import tech.kloos.kompound.internal.KompoundIcons

/**
 * The icons Kompound's own components draw, for apps that want the same look next to them (an edit button beside a [tech.kloos.kompound.inline.KInlineEdit],
 * a clear button, a check). Material Symbols paths (Apache-2.0); pass them to [KIcon] or any `icon` slot. For everything else bring your own
 * `ImageVector`s: Kompound does not depend on an icon library.
 */
public object KIcons {
    public val Search: ImageVector get() = KompoundIcons.Search
    public val Close: ImageVector get() = KompoundIcons.Close
    public val Check: ImageVector get() = KompoundIcons.Check
    public val Edit: ImageVector get() = KompoundIcons.Edit
    public val Add: ImageVector get() = KompoundIcons.Add
    public val Delete: ImageVector get() = KompoundIcons.Delete
    public val Error: ImageVector get() = KompoundIcons.Error
    public val ChevronDown: ImageVector get() = KompoundIcons.ChevronDown
    public val ChevronRight: ImageVector get() = KompoundIcons.ChevronRight
    public val MoreVertical: ImageVector get() = KompoundIcons.MoreVertical
    public val Eye: ImageVector get() = KompoundIcons.Eye
    public val EyeOff: ImageVector get() = KompoundIcons.EyeOff
    public val ChevronUp: ImageVector get() = KompoundIcons.ChevronUp
    public val Clock: ImageVector get() = KompoundIcons.Clock
    public val Palette: ImageVector get() = KompoundIcons.Palette
    public val Calendar: ImageVector get() = KompoundIcons.Calendar
}
