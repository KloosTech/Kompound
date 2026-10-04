package tech.kloos.kompound.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.dialog.KDialogDefaults
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText

/**
 * Adaptive overlay (COMPONENT_SPEC C-056): a draggable modal **bottom sheet** on windows narrower than
 * [dialogFromWidth], and a centred [KDialog] with the same content, title and actions on wider ones
 * (tablets, desktop, web). Show it by calling it from composition while it should be visible; the caller owns
 * the state.
 *
 * The sheet's drag, fling and scrim behaviour comes from Material 3's `ModalBottomSheet`; its container
 * colour and shape are parameters because that component draws them itself.
 *
 * @param onDismissRequest Called after the sheet has closed (drag down, scrim click, Escape/back, close
 * button) or when the dialog is dismissed.
 * @param modifier Modifier applied to the content column.
 * @param title Optional title, announced as a heading.
 * @param actions Optional footer, usually buttons, laid out at the end.
 * @param showCloseButton Shows a close icon button next to the title.
 * @param closeContentDescription Accessibility description of the close button (pass a localised string).
 * @param dialogFromWidth Window width from which the dialog replaces the sheet.
 * @param dismissOnClickOutside Whether a click on the scrim dismisses.
 * @param containerColor Sheet container colour; unspecified means `surfaceContainerLow`.
 * @param contentWindowInsets Insets the sheet content avoids; defaults to the navigation bar.
 * @param style Overrides merged over [KBottomSheetDefaults.style] (content padding and text style); applied
 * to the dialog too.
 * @param content Body; scrolls when taller than the available space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    showCloseButton: Boolean = false,
    closeContentDescription: String = KompoundTheme.strings.close,
    dialogFromWidth: Dp = KBottomSheetDefaults.DialogFromWidth,
    dismissOnClickOutside: Boolean = true,
    containerColor: Color = Color.Unspecified,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (windowWidth >= dialogFromWidth) {
        KDialog(
            onDismissRequest = onDismissRequest, modifier = modifier, title = title, actions = actions,
            showCloseButton = showCloseButton, closeContentDescription = closeContentDescription,
            dismissOnClickOutside = dismissOnClickOutside, style = style, content = content,
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val state = remember { MutableStyleState(null) }
    val titleState = remember { MutableStyleState(null) }
    fun closeAnimated() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = if (containerColor == Color.Unspecified) scheme.surfaceContainerLow else containerColor,
        scrimColor = KDialogDefaults.scrim(scheme.scrim),
        contentWindowInsets = { WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) },
        properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnClickOutside = dismissOnClickOutside),
    ) {
        Column(
            modifier = modifier.fillMaxWidth().windowInsetsPadding(contentWindowInsets).styleable(state, KBottomSheetDefaults.style(), style),
            verticalArrangement = Arrangement.spacedBy(KDialogDefaults.SectionSpacing),
        ) {
            if (title != null || showCloseButton) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title != null) {
                        KText(title, Modifier.weight(1f).semantics { heading() }.styleable(titleState, KDialogDefaults.titleStyle()))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    if (showCloseButton) {
                        KIconButton(onClick = { closeAnimated() }, contentDescription = closeContentDescription) {
                            KIcon(KompoundIcons.Close, contentDescription = null)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            if (actions != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

/** Defaults for [KBottomSheet]. */
public object KBottomSheetDefaults {
    /** Window width from which the adaptive overlay shows a dialog instead of a bottom sheet (compact < 600dp). */
    public val DialogFromWidth: Dp = 600.dp

    /** Content style: body text in `onSurface`, 24dp horizontal and 16dp bottom padding. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) {
            Style {
                contentColor(c.onSurface)
                textStyle(type.bodyMedium.copy(color = c.onSurface))
                contentPadding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 16.dp)
            }
        }
    }
}
