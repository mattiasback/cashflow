package app.cashflow.core.categorization

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.RuleMatch

/**
 * Matches rules against a payee key (upper-case normalized text). CONTAINS matches whole
 * words, so "ICA" matches "MAXI ICA STORMARKNAD" but not "AFRICA". Longer patterns win, so a
 * specific rule ("ICA FÖRSÄKRING") beats a general one ("ICA").
 */
class RuleEngine(rules: List<CategoryRule>) {

    private val compiled = rules
        .sortedByDescending { it.pattern.length }
        .map { rule -> rule to regexFor(rule) }

    fun firstMatch(payeeKey: String, rawUpper: String, amount: Money, accountId: AccountId? = null): CategoryRule? =
        compiled.firstOrNull { (rule, regex) ->
            signMatches(rule, amount) && limitsMatch(rule, amount, accountId) &&
                (regex.containsMatchIn(payeeKey) || regex.containsMatchIn(rawUpper))
        }?.first

    /** Whether one rule matches a row; used to count "Matchar N transaktioner". */
    fun matches(rule: CategoryRule, payeeKey: String, rawUpper: String, amount: Money, accountId: AccountId?): Boolean {
        val regex = regexFor(rule)
        return signMatches(rule, amount) && limitsMatch(rule, amount, accountId) &&
            (regex.containsMatchIn(payeeKey) || regex.containsMatchIn(rawUpper))
    }

    private fun limitsMatch(rule: CategoryRule, amount: Money, accountId: AccountId?): Boolean {
        if (rule.accountId != null && rule.accountId != accountId) return false
        val size = amount.abs()
        if (rule.minAmount != null && size < rule.minAmount!!) return false
        if (rule.maxAmount != null && size > rule.maxAmount!!) return false
        return true
    }

    /** How many of [keys] a rule would match; feeds "Använd på N tidigare köp". */
    fun countMatches(rule: CategoryRule, keys: List<String>): Int {
        val regex = regexFor(rule)
        return keys.count { regex.containsMatchIn(it) }
    }

    private fun signMatches(rule: CategoryRule, amount: Money) = when (rule.sign) {
        null -> true
        -1 -> amount.isNegative
        1 -> amount.isPositive
        else -> true
    }

    private fun regexFor(rule: CategoryRule): Regex {
        val p = Regex.escape(rule.pattern.uppercase())
        val body = when (rule.match) {
            RuleMatch.CONTAINS -> """(?<![\p{L}\d])$p(?![\p{L}\d])"""
            RuleMatch.STARTS_WITH -> "^$p"
            RuleMatch.EQUALS -> "^$p$"
        }
        return Regex(body)
    }
}
