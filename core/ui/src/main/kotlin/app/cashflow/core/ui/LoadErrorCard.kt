package app.cashflow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cashflow.core.designsystem.component.ErrorCard

/** "Det gick inte att läsa in" with "Försök igen", shared by the tabs. */
@Composable
fun LoadErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ErrorCard(
        title = stringResource(R.string.load_error_title),
        text = stringResource(R.string.load_error_text),
        retryLabel = stringResource(R.string.retry),
        onRetry = onRetry,
        modifier = modifier,
    )
}
