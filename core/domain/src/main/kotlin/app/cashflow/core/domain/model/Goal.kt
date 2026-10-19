package app.cashflow.core.domain.model

/** The household goals Hem and Flöde measure against. Insikter builds its plan on them. */
enum class GoalType(
    /** Used when no goal has been set. */
    val defaultTarget: Double,
    val range: ClosedFloatingPointRange<Double>,
) {
    /** How many months of fixed expenses the buffer (savings accounts) should cover. */
    BUFFER_MONTHS(6.0, 1.0..12.0),

    /** Share of income saved, in percent. */
    SAVINGS_RATE(10.0, 1.0..50.0),
}

data class Goal(val type: GoalType, val target: Double)

/** Every goal type, with the default where none is stored. */
data class Goals(val values: Map<GoalType, Double> = emptyMap()) {
    fun target(type: GoalType): Double = values[type] ?: type.defaultTarget
    val bufferMonths: Double get() = target(GoalType.BUFFER_MONTHS)
    val savingsRatePercent: Double get() = target(GoalType.SAVINGS_RATE)

    companion object {
        fun of(goals: List<Goal>) = Goals(goals.associate { it.type to it.target })
    }
}
