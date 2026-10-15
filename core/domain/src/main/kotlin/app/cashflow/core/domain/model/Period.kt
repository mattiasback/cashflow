package app.cashflow.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number

/** A calendar month, the unit of the cash-flow view. */
data class Period(val year: Int, val month: Int) : Comparable<Period> {
    init {
        require(month in 1..12) { "month must be 1..12" }
    }

    val first: LocalDate get() = LocalDate(year, month, 1)
    val last: LocalDate get() = next().first.let { LocalDate.fromEpochDays(it.toEpochDays() - 1) }

    fun next(): Period = if (month == 12) Period(year + 1, 1) else Period(year, month + 1)
    fun previous(): Period = if (month == 1) Period(year - 1, 12) else Period(year, month - 1)
    operator fun contains(date: LocalDate): Boolean = date.year == year && date.month.number == month

    override fun compareTo(other: Period): Int = compareValuesBy(this, other, Period::year, Period::month)

    override fun toString(): String = "$year-${month.toString().padStart(2, '0')}"

    companion object {
        fun of(date: LocalDate) = Period(date.year, date.month.number)
        fun parse(value: String): Period {
            val (y, m) = value.split('-')
            return Period(y.toInt(), m.toInt())
        }
    }
}

val Month.numberValue: Int get() = number
