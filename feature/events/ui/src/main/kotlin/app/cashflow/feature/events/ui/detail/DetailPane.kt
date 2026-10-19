package app.cashflow.feature.events.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.feature.events.ui.R

/** The transaction beside the list on wide windows: the sheet's content on a sheet-coloured pane. */
@Composable
internal fun DetailPane(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CashFlowTheme.colors.surfaceSheet)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = Spacing.s4),
    ) { content() }
}

/** Shown in the detail pane before a transaction is picked. */
@Composable
fun TransactionDetailPlaceholder() {
    val c = CashFlowTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.surfaceSheet).padding(Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(CfIcons.Transfer, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.detail_placeholder), style = CashFlowTheme.typography.body, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}
