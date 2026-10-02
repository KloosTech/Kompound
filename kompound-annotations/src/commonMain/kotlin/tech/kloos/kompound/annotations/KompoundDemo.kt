package tech.kloos.kompound.annotations

/**
 * Marks a `@Composable` function as a catalog demo. The function must be top-level and take no
 * parameters, optionally with a `DemoScope` extension receiver.
 *
 * @property id Stable, unique (per module) identifier, e.g. `button.primary`. Used in deep links.
 * @property title Human readable name.
 * @property description One sentence, at most 160 characters.
 * @property category Free string; use [KompoundCategory] constants for built-ins.
 * @property tags Free-form, normalised to lowercase by the catalog.
 * @property since Library version that introduced the component.
 * @property status One of [KompoundStatus].
 * @property platforms Platforms the demo supports; defaults to all.
 * @property aliases Former ids, kept so old deep links still resolve.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompoundDemo(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = KompoundCategory.Utilities,
    val tags: Array<String> = [],
    val since: String = "",
    val status: String = KompoundStatus.Stable,
    val platforms: Array<String> = [],
    val aliases: Array<String> = [],
)

public object KompoundCategory {
    public const val Inputs: String = "Inputs"
    public const val Buttons: String = "Buttons"
    public const val Display: String = "Display"
    public const val Feedback: String = "Feedback"
    public const val Navigation: String = "Navigation"
    public const val Layout: String = "Layout"
    public const val Overlays: String = "Overlays"
    public const val Data: String = "Data"
    public const val Animation: String = "Animation"
    public const val Utilities: String = "Utilities"
}

public object KompoundStatus {
    public const val Experimental: String = "Experimental"
    public const val Beta: String = "Beta"
    public const val Stable: String = "Stable"
    public const val Deprecated: String = "Deprecated"
}

public object KompoundPlatform {
    public const val Android: String = "Android"
    public const val Ios: String = "iOS"
    public const val Desktop: String = "Desktop"
    public const val Web: String = "Web"
}
