package tech.kloos.kompound.catalog.harness

import tech.kloos.kompound.demo.DemoEntry

/**
 * What a UI test harness asks the catalog to show: one demo in one environment (ADR 0008). Created from a deep link by [HarnessLaunch.parse].
 *
 * The link is `kompound://demo/<demo>?theme=dark&font=2.0&rtl=true&density=compact&lang=en&bare=true&control=Enabled::false&control=Steps::4`:
 *
 * | part | meaning | default |
 * |---|---|---|
 * | `<demo>` | the demo's id (`button.basic`) or its qualified id (`showcase/button.basic`) | required |
 * | `theme` | `light` or `dark` | `light` |
 * | `font` | font scale `0.5` to `3.0`, absolute (the device's own font scale is ignored) | `1.0` |
 * | `rtl` | `true` mirrors the layout | `false` |
 * | `density` | `compact`, `comfortable` or `spacious` | `comfortable` |
 * | `lang` | language of Kompound's own labels (`en`, `de`, `fr`, `es`, `it`) | `en` |
 * | `hue`, `saturation`, `roundness` | the catalog theme's seed colour (0..360, 0..1) and corner radius multiplier (0..2), see `ThemeSettings` | `262`, `0.55`, `1` |
 * | `chaos` | a seed: every colour role gets a random colour (no contrast guarantee) and the corner radii are random; for style fuzzing, see [ChaosTheme] | off |
 * | `bare` | only the demo and its controls, no catalog chrome | `true` |
 * | `control` (repeatable) | `Name::value` presets a control (see `DemoControls`); `::` separates because names may contain `=` | the demo's defaults |
 */
data class HarnessLaunch(
    val demo: String,
    val dark: Boolean = false,
    val fontScale: Float = 1f,
    val rtl: Boolean = false,
    val density: String = "comfortable",
    val lang: String = "en",
    val bare: Boolean = true,
    val hue: Float = 262f,
    val saturation: Float = 0.55f,
    val roundness: Float = 1f,
    val chaos: Long? = null,
    val controls: Map<String, String> = emptyMap(),
    /** Not part of the link: the launcher counts every received link, so opening the same link twice still starts the demo afresh (typed text and toggled controls do not leak between flows). */
    val nonce: Int = 0,
) {
    /** The entry this launch names, or `null` for an unknown demo. */
    fun resolve(entries: List<DemoEntry>): DemoEntry? = entries.firstOrNull { it.qualifiedId == demo } ?: entries.firstOrNull { it.meta.id == demo }

    companion object {
        const val Scheme: String = "kompound"
        const val Host: String = "demo"

        /** Reads a harness deep link; `null` when [uri] is not one (another scheme or host, no demo). */
        fun parse(uri: String): HarnessLaunch? {
            val prefix = "$Scheme://$Host/"
            if (!uri.startsWith(prefix)) return null
            val rest = uri.removePrefix(prefix).substringBefore('#')
            val demo = percentDecode(rest.substringBefore('?')).trim('/')
            if (demo.isEmpty()) return null
            val query = rest.substringAfter('?', "")
            val single = HashMap<String, String>()
            val controls = LinkedHashMap<String, String>()
            for (pair in query.split('&').filter { it.isNotEmpty() }) {
                val key = percentDecode(pair.substringBefore('='))
                val value = percentDecode(pair.substringAfter('=', ""))
                if (key == "control") {
                    val name = value.substringBefore("::")
                    if (name.isNotEmpty() && "::" in value) controls[name] = value.substringAfter("::")
                } else single[key] = value
            }
            return HarnessLaunch(
                demo = demo,
                dark = single["theme"] == "dark",
                fontScale = single["font"]?.toFloatOrNull()?.coerceIn(0.5f, 3f) ?: 1f,
                rtl = single["rtl"] == "true",
                density = single["density"]?.takeIf { it in setOf("compact", "comfortable", "spacious") } ?: "comfortable",
                lang = single["lang"]?.takeIf { it.isNotBlank() } ?: "en",
                bare = single["bare"] != "false",
                hue = single["hue"]?.toFloatOrNull()?.coerceIn(0f, 360f) ?: 262f,
                saturation = single["saturation"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.55f,
                roundness = single["roundness"]?.toFloatOrNull()?.coerceIn(0f, 2f) ?: 1f,
                chaos = single["chaos"]?.toLongOrNull(),
                controls = controls,
            )
        }

        /** Percent-decodes UTF-8 (`%20`, `%3D`, `%C3%A4`); `+` is a space. Malformed escapes are kept as they are. */
        internal fun percentDecode(text: String): String {
            if ('%' !in text && '+' !in text) return text
            val bytes = ArrayList<Byte>()
            var i = 0
            fun flush(out: StringBuilder) { if (bytes.isNotEmpty()) { out.append(bytes.toByteArray().decodeToString()); bytes.clear() } }
            val out = StringBuilder()
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && text.length - i >= 3 && text.substring(i + 1, i + 3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    bytes += text.substring(i + 1, i + 3).toInt(16).toByte()
                    i += 3
                } else {
                    flush(out)
                    out.append(if (c == '+') ' ' else c)
                    i++
                }
            }
            flush(out)
            return out.toString()
        }
    }
}
