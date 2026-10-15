package app.cashflow.feature.events.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import app.cashflow.core.designsystem.component.CategoryDot
import app.cashflow.core.designsystem.component.ListRow
import app.cashflow.core.designsystem.component.StatusBadge
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.ui.colors
import app.cashflow.feature.events.ui.R

/** A transaction in a list: category dot, payee, date · category, amount and status. */
@Composable
fun TxRow(
    row: TxRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    val colors = row.tone.colors()
    val category = row.category.asString()
    val badge = row.badge.asString()
    val description = stringResource(R.string.cd_tx_row, row.title, row.spokenAmount, row.date, category, badge)
    val openLabel = stringResource(R.string.cd_show_details)
    ListRow(
        title = row.title,
        subtitle = "${row.date} · $category",
        value = row.amount,
        valueColor = if (row.isIncome) CashFlowTheme.colors.textPositive else CashFlowTheme.colors.textPrimary,
        leading = { CategoryDot(color = colors.strong, fill = colors.fill) },
        trailing = { StatusBadge(badge, row.badgeKind) },
        showDivider = showDivider,
        onClick = onClick,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            onClick(label = openLabel) { onClick(); true }
        },
    )
}
