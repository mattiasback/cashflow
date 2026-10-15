package app.cashflow.core.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** An amount of Swedish kronor stored as öre, so sums never drift. */
@JvmInline
value class Money(val ore: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(ore + other.ore)
    operator fun minus(other: Money) = Money(ore - other.ore)
    operator fun unaryMinus() = Money(-ore)
    operator fun times(factor: Int) = Money(ore * factor)
    override fun compareTo(other: Money) = ore.compareTo(other.ore)

    val isNegative: Boolean get() = ore < 0
    val isPositive: Boolean get() = ore > 0
    fun abs() = Money(abs(ore))

    /** Whole kronor, rounded half away from zero. */
    val kronor: Long get() = if (ore >= 0) (ore + 50) / 100 else (ore - 50) / 100

    companion object {
        val ZERO = Money(0)
        fun kr(kronor: Long) = Money(kronor * 100)
        fun ofDouble(kronor: Double) = Money((kronor * 100).roundToLong())
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.ore })
