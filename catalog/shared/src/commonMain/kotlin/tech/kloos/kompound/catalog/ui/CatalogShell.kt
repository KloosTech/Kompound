package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone
import kotlinx.coroutines.launch
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.scaffold.KScaffold
import tech.kloos.kompound.scaffold.KTopBar
import tech.kloos.kompound.sheet.KBottomSheet
import tech.kloos.kompound.snackbar.KSnackbarHost
import tech.kloos.kompound.snackbar.KSnackbarHostState
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextArea
import tech.kloos.kompound.tooltip.KTooltip
import androidx.compose.foundation.gestures.Orientation

private const val RepoUrl = "https://github.com/KloosTech/Kompound"
private val WideBreakpoint = 840.dp
private val SidebarWidth = 340.dp

/** Adaptive layout: sidebar, detail and theme inspector side by side when wide; one pane with a back button when narrow. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CatalogShell(state: CatalogState, settings: ThemeSettings, dark: Boolean, onSettings: (ThemeSettings) -> Unit) {
    var showTheme by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    val snackbar = remember { KSnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val copyUsage: (String) -> Unit = { code ->
        clipboard.setText(AnnotatedString(code))
        scope.launch { snackbar.showSnackbar("Usage sample copied to the clipboard") }
    }
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.background)) {
        val wide = maxWidth >= WideBreakpoint
        // On a phone the system back gesture (Android back, iOS edge swipe) leaves the detail view for the list instead of closing the app.
        BackHandler(enabled = !wide && state.selected != null) { state.selectedId = null }
        // On a wide screen the detail pane always shows a listed component, like a master/detail app: when the filter
        // removes the selected one, the first remaining one is selected.
        LaunchedEffect(wide, state.visible) {
            val current = state.selected
            if (wide && (current == null || state.visible.none { it.qualifiedId == current.qualifiedId })) {
                state.selectedId = state.visible.firstOrNull()?.qualifiedId
            }
        }
        val topBarActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            KTooltip(if (dark) "Switch to light" else "Switch to dark") {
                KIconButton(onClick = { onSettings(settings.copy(mode = if (dark) ThemeMode.Light else ThemeMode.Dark)) }, contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode") {
                    KIcon(if (dark) CatalogIcons.LightMode else CatalogIcons.DarkMode, null)
                }
            }
            KTooltip("Theme designer") {
                KIconButton(onClick = { showTheme = !showTheme }, contentDescription = "Theme designer", variant = if (showTheme) KButtonVariant.Tonal else KButtonVariant.Text) {
                    KIcon(CatalogIcons.Palette, null)
                }
            }
            KTooltip("View on GitHub") {
                KIconButton(onClick = { uriHandler.openUri(RepoUrl) }, contentDescription = "View on GitHub") { KIcon(CatalogIcons.OpenInNew, null) }
            }
        }
        val detailTitle = state.selected?.meta?.title ?: "Kompound"

        KScaffold(
            topBar = if (!wide) ({
                KTopBar(
                    title = {
                        if (state.selected == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Logo(Modifier.padding(start = 8.dp))
                                KText("Kompound", style = textRole(weight = FontWeight.SemiBold) { it.titleLarge })
                                KBadge("alpha", tone = KBadgeTone.Info, emphasis = KBadgeEmphasis.Subtle)
                            }
                        } else {
                            KText(detailTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigation = if (state.selected != null) ({
                        KIconButton(onClick = { state.selectedId = null }, contentDescription = "Back to the list") { KIcon(CatalogIcons.ArrowBack, null) }
                    }) else null,
                    actions = topBarActions,
                )
            }) else null,
            snackbarHost = { KSnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets.navigationBars,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(SidebarWidth).fillMaxHeight().background(scheme.surfaceContainerLow)) {
                            Sidebar(state, onSelect = { state.selectedId = it.qualifiedId }, onOpenTags = { showTags = true })
                        }
                        KDivider(orientation = Orientation.Vertical)
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            KTopBar(title = detailTitle, actions = topBarActions)
                            KDivider()
                            DetailPane(state.selected, state.entries.size, Modifier.weight(1f), onCopy = copyUsage)
                        }
                        if (showTheme) {
                            KDivider(orientation = Orientation.Vertical)
                            Column(Modifier.width(SidebarWidth).fillMaxHeight().background(scheme.surfaceContainerLow)) {
                                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    KText("Theme designer", Modifier.weight(1f), style = textRole(weight = FontWeight.SemiBold) { it.titleMedium })
                                    KIconButton(onClick = { showTheme = false }, contentDescription = "Close theme designer") { KIcon(CatalogIcons.Close, null) }
                                }
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                                    ThemePanel(settings, onSettings, onReset = { onSettings(ThemeSettings()) }, onGetCode = { showCode = true })
                                }
                            }
                        }
                    }
                } else if (state.selected != null) {
                    DetailPane(state.selected, state.entries.size, onCopy = copyUsage)
                } else {
                    Sidebar(state, onSelect = { state.selectedId = it.qualifiedId }, onOpenTags = { showTags = true }, showHeader = false)
                }
            }
        }

        if (!wide && showTheme) {
            KBottomSheet(onDismissRequest = { showTheme = false }, title = "Theme designer", showCloseButton = true) {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    ThemePanel(settings, onSettings, onReset = { onSettings(ThemeSettings()) }, onGetCode = { showCode = true })
                }
            }
        }
        if (showTags) TagFilterSheet(state) { showTags = false }
        if (showCode) {
            val code = settings.toKotlin()
            KDialog(
                onDismissRequest = { showCode = false },
                title = "Theme code",
                actions = {
                    KButton(onClick = { showCode = false }, variant = KButtonVariant.Text) { KText("Close") }
                    KButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                        scope.launch { snackbar.showSnackbar("Theme code copied to the clipboard") }
                    }) { KIcon(CatalogIcons.Copy, null); KText("Copy", Modifier.padding(start = 8.dp)) }
                },
            ) {
                KText("Paste this into your app to use the theme you designed.", Modifier.padding(bottom = 12.dp), style = textRole(quiet = true) { it.bodyMedium })
                KTextArea(code, {}, readOnly = true, minLines = 10, maxLines = 14)
            }
        }
    }
}
