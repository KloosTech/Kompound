package tech.kloos.kompound.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.KJsonException

/** What an import produced: the [theme], and what was not understood or not found ([warnings], for showing to the user). */
public class KThemeImportResult(public val theme: KThemeSpec, public val warnings: List<String>)

/**
 * Reads a colour theme exported from a design tool into a [KThemeSpec]:
 *
 * - [fromMaterialThemeBuilder]: the JSON of Google's Material Theme Builder (`schemes.light`, `schemes.dark`, with keys such as `primary`,
 *   `onPrimaryContainer`, `surfaceContainerHigh`), also the medium and high contrast variants.
 * - [fromDesignTokens]: design-token JSON as written by Figma plugins such as Tokens Studio or the W3C format (`{"$value": "#6750A4"}` or
 *   `{"value": "#6750A4", "type": "color"}`), grouped as you like. A token is matched to a colour role by the end of its path
 *   (`md.sys.color.on-primary`, `color/light/onPrimary`, `Colors.Primary Container` all work); a path with `dark` in it (or a top-level
 *   `dark` group) belongs to the dark scheme, everything else to the light one. References such as `"{color.primary}"` are followed.
 *   Tokens called `success`, `warning`, `info` (with `on…` and `…Container` variants) fill Kompound's extra colours.
 *
 * Roles that are not in the file keep the Material defaults, and every unknown key is listed in [KThemeImportResult.warnings].
 */
public object KThemeImport {
    /** Which variant of a Material Theme Builder file to read. */
    public enum class Contrast(internal val suffix: String) { Standard(""), Medium("-medium-contrast"), High("-high-contrast") }

    /** Reads a Material Theme Builder export. Throws [KJsonException] when [json] is not JSON or has no `schemes`. */
    public fun fromMaterialThemeBuilder(json: String, name: String = "Imported", contrast: Contrast = Contrast.Standard): KThemeImportResult {
        val root = JsonValue.parse(json) as? JsonObject ?: throw KJsonException("A Material Theme Builder file is a JSON object")
        val schemes = root["schemes"] as? JsonObject ?: throw KJsonException("No \"schemes\" in the file: is it a Material Theme Builder export?")
        val warnings = ArrayList<String>()
        fun read(mode: String, base: ColorScheme): ColorScheme {
            val set = (schemes[mode + contrast.suffix] ?: schemes[mode]) as? JsonObject
            if (set == null) { warnings += "No \"$mode\" scheme: Material defaults are used"; return base }
            var scheme = base
            for ((key, value) in set.fields) {
                val color = (value as? JsonString)?.value?.let(::parseColor)
                if (color == null) { warnings += "$mode.$key is not a colour"; continue }
                val setter = Roles[normalize(key)]
                if (setter == null) warnings += "$mode.$key is not used by Kompound" else scheme = setter(scheme, color)
            }
            return scheme
        }
        val light = read("light", lightColorScheme())
        val dark = read("dark", darkColorScheme())
        return KThemeImportResult(KThemeSpec(name, light, dark), warnings)
    }

    /** Reads design tokens (see the class description). Throws [KJsonException] when [json] is not a JSON object. */
    public fun fromDesignTokens(json: String, name: String = "Imported"): KThemeImportResult {
        val root = JsonValue.parse(json) as? JsonObject ?: throw KJsonException("A design token file is a JSON object")
        val tokens = LinkedHashMap<String, String>()   // dotted path (lower case) -> raw value
        val originals = LinkedHashMap<String, String>() // dotted path -> path as written
        fun walk(value: JsonValue, path: List<String>) {
            val obj = value as? JsonObject ?: return
            val leaf = (obj["\$value"] ?: obj["value"]) as? JsonString
            if (leaf != null) {
                val key = path.joinToString(".").lowercase()
                tokens[key] = leaf.value
                originals[key] = path.joinToString(".")
            } else {
                for ((k, v) in obj.fields) if (!k.startsWith("$")) walk(v, path + k)
            }
        }
        walk(root, emptyList())
        if (tokens.isEmpty()) throw KJsonException("No tokens found: expected objects with \"\$value\" or \"value\"")

        fun resolve(raw: String, depth: Int = 0): String {
            val ref = raw.trim().takeIf { it.startsWith("{") && it.endsWith("}") }?.drop(1)?.dropLast(1)?.trim()?.lowercase() ?: return raw
            if (depth > 16) return raw
            val target = tokens[ref] ?: tokens.entries.firstOrNull { it.key.endsWith(".$ref") }?.value ?: return raw
            return resolve(target, depth + 1)
        }

        val warnings = ArrayList<String>()
        var light = lightColorScheme()
        var dark = darkColorScheme()
        var lightColors = KompoundColors.Light
        var darkColors = KompoundColors.Dark
        var seenLight = false
        var seenDark = false
        // Longest role name first, so "onprimarycontainer" is not mistaken for "primarycontainer" or "container".
        val roleNames = (Roles.keys + ExtraRoles.keys).sortedByDescending { it.length }
        for ((key, raw) in tokens) {
            val color = parseColor(resolve(raw))
            if (color == null) {
                // Only colour-looking tokens are worth a warning: sizes, fonts and the like are expected in a token file.
                if (raw.trim().startsWith("#") || raw.trim().startsWith("{")) warnings += "${originals.getValue(key)} is not a colour"
                continue
            }
            val segments = key.split(".")
            val isDark = segments.any { it == "dark" || it.startsWith("dark-") || it.endsWith("-dark") || it.endsWith("_dark") || it == "darkmode" }
            val normalized = normalize(segments.filter { it != "dark" && it != "light" }.joinToString(""))
            val role = roleNames.firstOrNull { normalized.endsWith(it) }
            if (role == null) { warnings += "${originals.getValue(key)} is not a colour role Kompound uses"; continue }
            if (isDark) seenDark = true else seenLight = true
            Roles[role]?.let { set -> if (isDark) dark = set(dark, color) else light = set(light, color) }
            ExtraRoles[role]?.let { set -> if (isDark) darkColors = set(darkColors, color) else lightColors = set(lightColors, color) }
        }
        if (!seenLight) warnings += "No light colours found: Material defaults are used for the light scheme"
        if (!seenDark) warnings += "No dark colours found: Material defaults are used for the dark scheme"
        return KThemeImportResult(KThemeSpec(name, light, dark, lightColors, darkColors), warnings)
    }

    /** Lower case without separators: `on-primary`, `on_primary`, `onPrimary` and `On Primary` all become `onprimary`. */
    private fun normalize(name: String): String = name.filter { it.isLetterOrDigit() }.lowercase()

    private val Roles: Map<String, (ColorScheme, Color) -> ColorScheme> = mapOf(
        "primary" to { s, c -> s.copy(primary = c) }, "onprimary" to { s, c -> s.copy(onPrimary = c) },
        "primarycontainer" to { s, c -> s.copy(primaryContainer = c) }, "onprimarycontainer" to { s, c -> s.copy(onPrimaryContainer = c) },
        "inverseprimary" to { s, c -> s.copy(inversePrimary = c) },
        "secondary" to { s, c -> s.copy(secondary = c) }, "onsecondary" to { s, c -> s.copy(onSecondary = c) },
        "secondarycontainer" to { s, c -> s.copy(secondaryContainer = c) }, "onsecondarycontainer" to { s, c -> s.copy(onSecondaryContainer = c) },
        "tertiary" to { s, c -> s.copy(tertiary = c) }, "ontertiary" to { s, c -> s.copy(onTertiary = c) },
        "tertiarycontainer" to { s, c -> s.copy(tertiaryContainer = c) }, "ontertiarycontainer" to { s, c -> s.copy(onTertiaryContainer = c) },
        "background" to { s, c -> s.copy(background = c) }, "onbackground" to { s, c -> s.copy(onBackground = c) },
        "surface" to { s, c -> s.copy(surface = c) }, "onsurface" to { s, c -> s.copy(onSurface = c) },
        "surfacevariant" to { s, c -> s.copy(surfaceVariant = c) }, "onsurfacevariant" to { s, c -> s.copy(onSurfaceVariant = c) },
        "surfacetint" to { s, c -> s.copy(surfaceTint = c) }, "inversesurface" to { s, c -> s.copy(inverseSurface = c) },
        "inverseonsurface" to { s, c -> s.copy(inverseOnSurface = c) },
        "error" to { s, c -> s.copy(error = c) }, "onerror" to { s, c -> s.copy(onError = c) },
        "errorcontainer" to { s, c -> s.copy(errorContainer = c) }, "onerrorcontainer" to { s, c -> s.copy(onErrorContainer = c) },
        "outline" to { s, c -> s.copy(outline = c) }, "outlinevariant" to { s, c -> s.copy(outlineVariant = c) }, "scrim" to { s, c -> s.copy(scrim = c) },
        "surfacebright" to { s, c -> s.copy(surfaceBright = c) }, "surfacedim" to { s, c -> s.copy(surfaceDim = c) },
        "surfacecontainer" to { s, c -> s.copy(surfaceContainer = c) }, "surfacecontainerhigh" to { s, c -> s.copy(surfaceContainerHigh = c) },
        "surfacecontainerhighest" to { s, c -> s.copy(surfaceContainerHighest = c) }, "surfacecontainerlow" to { s, c -> s.copy(surfaceContainerLow = c) },
        "surfacecontainerlowest" to { s, c -> s.copy(surfaceContainerLowest = c) },
    )

    private val ExtraRoles: Map<String, (KompoundColors, Color) -> KompoundColors> = mapOf(
        "success" to { k, c -> k.copy(success = c) }, "onsuccess" to { k, c -> k.copy(onSuccess = c) },
        "successcontainer" to { k, c -> k.copy(successContainer = c) }, "onsuccesscontainer" to { k, c -> k.copy(onSuccessContainer = c) },
        "warning" to { k, c -> k.copy(warning = c) }, "onwarning" to { k, c -> k.copy(onWarning = c) },
        "warningcontainer" to { k, c -> k.copy(warningContainer = c) }, "onwarningcontainer" to { k, c -> k.copy(onWarningContainer = c) },
        "info" to { k, c -> k.copy(info = c) }, "oninfo" to { k, c -> k.copy(onInfo = c) },
        "infocontainer" to { k, c -> k.copy(infoContainer = c) }, "oninfocontainer" to { k, c -> k.copy(onInfoContainer = c) },
    )

    /**
     * A colour from CSS-like text: `#RGB`, `#RRGGBB`, `#RRGGBBAA` (alpha last, as in CSS and Figma), `rgb(12, 34, 56)` and `rgba(12, 34, 56, 0.5)`
     * (alpha 0..1 or a percentage). `null` when [text] is none of those.
     */
    public fun parseColor(text: String): Color? {
        val t = text.trim()
        if (t.startsWith("#")) {
            val hex = t.drop(1)
            if (hex.any { it !in "0123456789abcdefABCDEF" }) return null
            fun h(i: Int, n: Int) = hex.substring(i, i + n).toInt(16)
            return when (hex.length) {
                3 -> Color(h(0, 1) * 17, h(1, 1) * 17, h(2, 1) * 17)
                4 -> Color(h(0, 1) * 17, h(1, 1) * 17, h(2, 1) * 17, h(3, 1) * 17)
                6 -> Color(h(0, 2), h(2, 2), h(4, 2))
                8 -> Color(h(0, 2), h(2, 2), h(4, 2), h(6, 2))
                else -> null
            }
        }
        val fn = Regex("""^rgba?\(\s*([^)]*)\)$""", RegexOption.IGNORE_CASE).matchEntire(t) ?: return null
        val parts = fn.groupValues[1].split(',', '/', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size !in 3..4) return null
        fun channel(p: String): Int? = if (p.endsWith("%")) p.dropLast(1).toFloatOrNull()?.let { (it * 2.55f).toInt().coerceIn(0, 255) } else p.toFloatOrNull()?.toInt()?.coerceIn(0, 255)
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        val a = parts.getOrNull(3)?.let { p -> if (p.endsWith("%")) p.dropLast(1).toFloatOrNull()?.div(100f) else p.toFloatOrNull() }?.coerceIn(0f, 1f) ?: 1f
        return Color(r, g, b, (a * 255f).toInt())
    }
}
