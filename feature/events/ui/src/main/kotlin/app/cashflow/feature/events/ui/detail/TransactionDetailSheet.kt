package app.cashflow.feature.events.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.component.SheetHeader
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.core.ui.colors
import app.cashflow.feature.events.ui.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionDetailRoot(
    txId: String,
    onDismiss: () -> Unit,
    viewModel: TransactionDetailViewModel = koinViewModel(key = txId) { parametersOf(txId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TransactionDetailEvent.Dismiss -> onDismiss()
        }
    }
    TransactionDetailSheet(state, viewModel::onAction)
}

/** Content of the transaction sheet (the sheet itself is a Nav3 scene in :app). */
@Composable
fun TransactionDetailSheet(
    state: TransactionDetailState,
    onAction: (TransactionDetailAction) -> Unit,
) {
    val c = CashFlowTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item {
            SheetHeader(title = state.title, eyebrow = stringResource(R.string.detail_eyebrow), figure = state.amount)
        }
        if (!state.picking) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    KeyValue(stringResource(R.string.detail_category), state.category?.asString().orEmpty(), Modifier.weight(1f))
                    KeyValue(stringResource(R.string.detail_date), state.transactionDate, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    KeyValue(stringResource(R.string.detail_account), state.account, Modifier.weight(1f))
                    KeyValue(stringResource(R.string.detail_booked), state.bookingDate, Modifier.weight(1f))
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(Radius.sm).background(if (c.isDark) c.surfaceCard else c.surfaceGroup).padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.detail_how_title), style = CashFlowTheme.typography.caption, color = c.textSecondary)
                    state.howLabel?.let {
                        Text(
                            it.asString(),
                            style = CashFlowTheme.typography.caption,
                            color = c.textOnEventsTint,
                            modifier = Modifier.clip(Radius.pill).background(c.accentEventsTint).padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                    state.howText?.let { Text(it.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary) }
                    state.partner?.let { Text(stringResource(R.string.detail_partner, it), style = CashFlowTheme.typography.body, color = c.textPrimary) }
                    Text(stringResource(R.string.detail_raw, state.rawText), style = CashFlowTheme.typography.label, color = c.textSecondary)
                    Text(stringResource(R.string.detail_id, state.shortId), style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    CfButton(stringResource(R.string.detail_change_category), { onAction(TransactionDetailAction.OnChangeCategoryClick) }, Modifier.fillMaxWidth())
                    if (state.canMarkHousehold) {
                        CfButton(
                            stringResource(R.string.detail_mark_household),
                            { onAction(TransactionDetailAction.OnMarkHouseholdClick) },
                            Modifier.fillMaxWidth(),
                            variant = CfButtonVariant.Outline,
                        )
                    }
                }
            }
        } else {
            var lastGroup: UiText? = null
            state.options.forEach { o ->
                if (o.group != lastGroup) {
                    val g = o.group
                    item(key = "g-${o.id}") {
                        Text(g.asString().uppercase(), style = CashFlowTheme.typography.overline, color = c.textSecondary)
                    }
                    lastGroup = o.group
                }
                item(key = o.id) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchMin)
                            .clip(Radius.sm)
                            .clickable(role = Role.RadioButton) { onAction(TransactionDetailAction.OnCategoryPick(o.id)) }
                            .semantics { selected = o.selected }
                            .padding(horizontal = Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(Radius.xs).background(o.tone.colors().strong))
                        Spacer(Modifier.width(Spacing.s3))
                        Text(o.name.asString(), style = CashFlowTheme.typography.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                        if (o.selected) Icon(CfIcons.Check, contentDescription = null, tint = c.accentEventsInk, modifier = Modifier.size(20.dp))
                    }
                }
            }
            item {
                CfSwitchRow(
                    label = stringResource(R.string.review_remember, state.title),
                    checked = state.remember,
                    onCheckedChange = { onAction(TransactionDetailAction.OnRememberToggle(it)) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    CfButton(stringResource(R.string.detail_cancel), { onAction(TransactionDetailAction.OnCancelClick) }, Modifier.weight(1f), variant = CfButtonVariant.Outline)
                    CfButton(stringResource(R.string.detail_save), { onAction(TransactionDetailAction.OnSaveClick) }, Modifier.weight(1f), enabled = state.canSave)
                }
            }
        }
        state.error?.let { err ->
            item { Text(err.asString(), style = CashFlowTheme.typography.label, color = c.dangerStrong) }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    Column(
        modifier.clip(Radius.sm).background(if (c.isDark) c.surfaceCard else c.surfaceGroup).padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
        Text(value, style = CashFlowTheme.typography.title.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 2)
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionDetailPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        TransactionDetailSheet(
            TransactionDetailState(
                title = "Matbutik Exempel",
                amount = "−412,50 kr",
                category = UiText.DynamicString("Mat"),
                transactionDate = "2026-10-11",
                bookingDate = "2026-10-12",
                account = "Lönekonto",
                howLabel = UiText.DynamicString("Inbyggd regel"),
                howText = UiText.DynamicString("Texten matchade en inbyggd regel för kända kedjor och tjänster."),
                rawText = "MATBUTIK EXEMPEL,STAD,SE",
                shortId = "#3fa1c2b9",
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
