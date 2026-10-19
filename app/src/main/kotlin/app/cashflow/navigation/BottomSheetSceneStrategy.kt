package app.cashflow.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius

/** Renders an entry inside a ModalBottomSheet (Nav3 bottom-sheet recipe). */
@OptIn(ExperimentalMaterial3Api::class)
internal data class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        ModalBottomSheet(
            onDismissRequest = onBack,
            // Sheets open to their full content height, so actions at the bottom (undo, save) are visible.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CashFlowTheme.colors.surfaceSheet,
            scrimColor = CashFlowTheme.colors.scrim,
            shape = Radius.sheetTop,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }
}

/**
 * Shows entries whose metadata contains [SHEET_KEY] as bottom sheets. Sheets that own a
 * ViewModel (transaction detail, import receipt) are destinations; small pickers are not.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[SHEET_KEY] != true) return null
        return BottomSheetScene(
            key = last.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = last,
            onBack = onBack,
        )
    }

    companion object {
        const val SHEET_KEY = "cashflow.bottomSheet"
        val sheet: Map<String, Any> = mapOf(SHEET_KEY to true)
    }
}
