package app.cashflow.feature.rules.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.ui.format.AppLocale
import app.cashflow.core.ui.format.MoneyFormatter

/** What a rule matches, formatted in the UI: "börjar med "ICA", över 5 000 kr". [match] null = a plain list of names. */
@Immutable
data class RuleMatchUi(
    val match: RuleMatch?,
    val text: String,
    val over: String? = null,
    val under: String? = null,
    val account: String? = null,
)

fun CategoryRule.toMatchUi(locale: AppLocale, accountName: String? = null) = RuleMatchUi(
    match = match,
    text = pattern,
    over = minAmount?.let { MoneyFormatter.format(it, locale = locale) },
    under = maxAmount?.let { MoneyFormatter.format(it, locale = locale) },
    account = accountName,
)

@Composable
fun RuleMatchUi.asString(): String {
    val head = when (match) {
        RuleMatch.STARTS_WITH -> stringResource(R.string.rules_match_starts, text)
        RuleMatch.CONTAINS -> stringResource(R.string.rules_match_contains, text)
        RuleMatch.EQUALS -> stringResource(R.string.rules_match_equals, text)
        null -> text
    }
    val parts = listOfNotNull(
        head,
        over?.let { stringResource(R.string.rules_match_over, it) },
        under?.let { stringResource(R.string.rules_match_under, it) },
        account?.let { stringResource(R.string.rules_match_account, it) },
    )
    return parts.joinToString(", ")
}
