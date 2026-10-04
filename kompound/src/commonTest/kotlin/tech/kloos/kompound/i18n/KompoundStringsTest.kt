package tech.kloos.kompound.i18n

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.Column
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.avatar.KAvatarStatus
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KompoundStringsBundlesTest {
    private val bundles = mapOf(
        "de" to KompoundStrings.German, "fr" to KompoundStrings.French, "es" to KompoundStrings.Spanish, "it" to KompoundStrings.Italian,
    )

    /** Words that are the same in the language as in English (names, abbreviations, loan words). */
    private val sameAsEnglish = mapOf(
        "de" to setOf("ok", "statusOnline", "statusOffline", "minute", "am", "pm"),
        "fr" to setOf("ok", "minute", "saturation", "am", "pm"),
        "es" to emptySet(),
        "it" to setOf("ok", "statusOnline", "statusOffline", "am", "pm"),
    )

    @Test
    fun everyBundleTranslatesEveryText() {
        val english = KompoundStrings.English.texts()
        for ((tag, bundle) in bundles) {
            val texts = bundle.texts()
            assertEquals(english.keys, texts.keys)
            val untranslated = texts.filter { (k, v) -> v == english.getValue(k) }.keys
            assertEquals(sameAsEnglish.getValue(tag), untranslated, "$tag: unexpectedly identical to English (or newly translated: update the list)")
            assertTrue(texts.values.none { it.isBlank() }, "$tag has an empty text")
        }
    }

    @Test
    fun theLanguageTagPicksTheBundleAndUnknownLanguagesFallBackToEnglish() {
        assertEquals(KompoundStrings.German, KompoundStrings.forLanguageTag("de"))
        assertEquals(KompoundStrings.German, KompoundStrings.forLanguageTag("de-AT"))
        assertEquals(KompoundStrings.French, KompoundStrings.forLanguageTag("fr_CA"))
        assertEquals(KompoundStrings.Spanish, KompoundStrings.forLanguageTag("ES"))
        assertEquals(KompoundStrings.Italian, KompoundStrings.forLanguageTag("it-CH"))
        assertEquals(KompoundStrings.English, KompoundStrings.forLanguageTag("ja"))
        assertEquals(KompoundStrings.English, KompoundStrings.forLanguageTag(""))
    }

    @Test
    fun namedAndCountedTextsAreFormattedPerLanguage() {
        assertEquals("Remove tag", KompoundStrings.English.removeNamed("tag"))
        assertEquals("tag entfernen", KompoundStrings.German.removeNamed("tag"))
        assertEquals("Chart, 4 values from 1 to 9", KompoundStrings.English.chartSummary(4, "1", "9"))
        assertNotEquals(KompoundStrings.English.chartSummary(4, "1", "9"), KompoundStrings.French.chartSummary(4, "1", "9"))
    }

    @Test
    fun copyChangesOneTextAndKeepsTheRest() {
        val custom = KompoundStrings.English.copy(close = "Dismiss")
        assertEquals("Dismiss", custom.close)
        assertEquals(KompoundStrings.English.cancel, custom.cancel)
    }
}

@OptIn(ExperimentalTestApi::class)
class KompoundStringsUiTest {
    private fun androidx.compose.ui.test.ComposeUiTest.onNodeDescription(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

    @Test
    fun componentsUseTheThemesLanguageAndEnglishWithoutATheme() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                Column {
                    KAvatar("Ada", Modifier.testTag("plain"), status = KAvatarStatus.Away)
                    KompoundTheme(lightColorScheme(), strings = KompoundStrings.German) { KAvatar("Ada", Modifier.testTag("german"), status = KAvatarStatus.Away) }
                    KompoundTheme(lightColorScheme(), strings = KompoundStrings.English.copy(statusAway = "stepped out")) { KAvatar("Ada", Modifier.testTag("custom"), status = KAvatarStatus.Away) }
                }
            }
        }
        assertEquals("Ada, away", onNodeDescription("plain"))
        assertEquals("Ada, abwesend", onNodeDescription("german"))
        assertEquals("Ada, stepped out", onNodeDescription("custom"))
    }
}
