package tech.kloos.kompound.catalog.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import tech.kloos.kompound.catalog.harness.HarnessLaunch
import tech.kloos.kompound.catalog.ui.KompoundCatalog

class MainActivity : ComponentActivity() {
    // Only the `maestro` build type has the harness: a deep link then opens one demo in a given environment (ADR 0008).
    private var launch by mutableStateOf<HarnessLaunch?>(null)

    private fun harnessLaunch(intent: Intent?): HarnessLaunch? =
        if (BuildConfig.HARNESS && intent?.action == Intent.ACTION_VIEW) intent.dataString?.let(HarnessLaunch::parse) else null

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launch = harnessLaunch(intent)
        setContent {
            // Test tags become Android resource ids (`id:` selectors in Maestro) in the harness build only.
            val tags = if (BuildConfig.HARNESS) Modifier.semantics { testTagsAsResourceId = true } else Modifier
            Box(Modifier.fillMaxSize().then(tags)) { KompoundCatalog(launch = launch) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        harnessLaunch(intent)?.let { launch = it }
    }
}
