package app.cashflow.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius

/**
 * The app's bottom sheet: opens at its full content height (so bottom actions such as undo or
 * save are visible at once) and keeps its content above the system navigation bar, whether that
 * is gesture navigation or three buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = CashFlowTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceSheet,
        scrimColor = c.scrim,
        shape = Radius.sheetTop,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), content = content)
    }
}
