package app.cashflow.feature.importer.ui.receipt

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AnimatedCheck
import app.cashflow.core.designsystem.component.CfButton
import app.cashflow.core.designsystem.component.CfButtonVariant
import app.cashflow.core.designsystem.component.CountUpText
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Radius
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.feature.importer.ui.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ImportReceiptRoot(
    importIds: List<String>,
    onDismiss: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: ImportReceiptViewModel = koinViewModel(key = importIds.joinToString()) { parametersOf(importIds) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ImportReceiptEvent.Dismiss -> onDismiss()
            ImportReceiptEvent.OpenReview -> onOpenReview()
        }
    }
    ImportReceiptSheet(state, viewModel::onAction)
}

@Composable
fun ImportReceiptSheet(
    state: ImportReceiptState,
    onAction: (ImportReceiptAction) -> Unit,
    animate: Boolean = true,
) {
    val c = CashFlowTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s6)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedCheck(animate = animate)
            Spacer(Modifier.width(Spacing.s3))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.receipt_title), style = CashFlowTheme.typography.title, color = c.textPrimary)
                Text(listOf(state.files, state.accounts).filter { it.isNotBlank() }.joinToString(" · "), style = CashFlowTheme.typography.label, color = c.textSecondary, maxLines = 3)
            }
        }
        val stats = listOf(
            state.newCount to R.plurals.receipt_new,
            state.duplicateCount to R.plurals.receipt_duplicates,
            state.transferCount to R.plurals.receipt_transfers,
            state.reviewCount to R.plurals.receipt_review,
        )
        stats.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                pair.forEachIndexed { i, (count, label) ->
                    val text = pluralStringResource(label, count, count)
                    StatBox(count, text.substringAfter("$count").trim(), text, (rowIndex * 2 + i) * 80, animate, Modifier.weight(1f))
                }
            }
        }
        Text(
            pluralStringResource(R.plurals.receipt_auto, state.autoCount, state.autoCount),
            style = CashFlowTheme.typography.label,
            color = c.textSecondary,
        )
        if (state.warningCount > 0) {
            Text(
                pluralStringResource(R.plurals.receipt_warnings, state.warningCount, state.warningCount),
                style = CashFlowTheme.typography.label,
                color = c.dangerStrong,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            CfButton(stringResource(R.string.receipt_done), { onAction(ImportReceiptAction.OnDoneClick) }, Modifier.weight(1f), variant = CfButtonVariant.Outline)
            if (state.reviewCount > 0) {
                CfButton(stringResource(R.string.receipt_review_now), { onAction(ImportReceiptAction.OnReviewClick) }, Modifier.weight(1f), variant = CfButtonVariant.Accent, accent = c.actionEvents)
            }
        }
    }
}

/** A counter that slides in staggered and rolls up from zero. */
@Composable
private fun StatBox(count: Int, label: String, spoken: String, delayMs: Int, animate: Boolean, modifier: Modifier = Modifier) {
    val c = CashFlowTheme.colors
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300, delayMillis = delayMs)) }
    Column(
        modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 24f
            }
            .clip(Radius.xs)
            .background(c.surfaceGroup)
            .padding(horizontal = Spacing.s3, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        CountUpText(count, animate = animate)
        Text(label, style = CashFlowTheme.typography.label, color = c.textSecondary)
    }
}


@Preview(showBackground = true)
@Composable
private fun ReceiptPreview() {
    app.cashflow.core.designsystem.theme.CashFlowTheme {
        ImportReceiptSheet(
            ImportReceiptState(files = "Kontoutdrag.xls", accounts = "Lönekonto", newCount = 38, duplicateCount = 6, transferCount = 9, reviewCount = 3, autoCount = 26, isLoading = false),
            onAction = {},
            animate = false,
        )
    }
}
