package tech.kloos.kompound

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Turns on foundation's inherited text style support, which `contentColor` and text properties of a
 * `Style` need to reach child text ([tech.kloos.kompound.text.KText]). Components call [ensureEnabled]
 * themselves, so consumers normally do not need to.
 */
public object KompoundStyles {
    private var enabled = false

    @OptIn(ExperimentalFoundationApi::class)
    public fun ensureEnabled() {
        if (enabled) return
        ComposeFoundationFlags.isInheritedTextStyleEnabled = true
        enabled = true
    }
}
