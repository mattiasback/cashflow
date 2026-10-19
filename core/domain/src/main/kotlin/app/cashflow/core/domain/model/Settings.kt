package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** "Privat" or "Gemensamt": which household view opens first. */
enum class HouseholdScope { PRIVATE, JOINT }

enum class MortgageSplitMode {
    /** You enter the monthly amortization; the rest of each payment is interest. */
    AMORTIZATION_AMOUNT,

    /** You enter the interest share of each payment; the rest is amortization. */
    INTEREST_SHARE,
}

/**
 * "Visa amortering som sparande". Off by default: a mortgage payment is one expense (Boende).
 * On, each payment whose text matches [pattern] is split into interest (expense, Boende) and
 * amortization (savings). It is applied when totals are computed, so turning it off restores
 * the original totals exactly.
 */
data class MortgageSplit(
    val enabled: Boolean = false,
    val mode: MortgageSplitMode = MortgageSplitMode.AMORTIZATION_AMOUNT,
    /** Monthly amortization, for [MortgageSplitMode.AMORTIZATION_AMOUNT]. */
    val amortization: Money = Money.ZERO,
    /** Interest share of a payment in percent (0–100), for [MortgageSplitMode.INTEREST_SHARE]. */
    val interestSharePercent: Int = 0,
    /** Upper-case text that marks a mortgage payment. */
    val pattern: String = "BOLÅN",
) {
    /** The amortization part of one payment of [paid] (a positive amount). */
    fun amortizationOf(paid: Money): Money {
        if (!enabled || !paid.isPositive) return Money.ZERO
        val part = when (mode) {
            MortgageSplitMode.AMORTIZATION_AMOUNT -> amortization
            MortgageSplitMode.INTEREST_SHARE -> Money(paid.ore * (100 - interestSharePercent.coerceIn(0, 100)) / 100)
        }
        return Money(part.ore.coerceIn(0, paid.ore))
    }

    fun matches(rawText: String): Boolean {
        val upper = rawText.uppercase()
        return pattern.isNotBlank() && (upper.contains(pattern) || upper.contains(pattern.replace('Å', 'A')))
    }
}

/** Small settings with no queries, kept in DataStore. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val percentDecimals: Boolean = false,
    val scope: HouseholdScope = HouseholdScope.JOINT,
    val mortgage: MortgageSplit = MortgageSplit(),
    /** The tab and month the app opened on last, restored at start. */
    val lastTab: String? = null,
    val lastPeriod: String? = null,
    /** Flags for one-time tips, e.g. "card deck explained". */
    val seenFlags: Set<String> = emptySet(),
    /** Insikter plan steps and suggestions marked "klart", by their stable ids. */
    val insightsDone: Set<String> = emptySet(),
    /** The buffer balance you entered; flows after its date move it (see Buffer.estimate). */
    val bufferBalance: BufferBalance? = null,
)

/** A balance of the buffer account on a day, entered by hand since the exports have none. */
data class BufferBalance(val amount: Money, val date: LocalDate)
