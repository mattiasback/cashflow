package app.cashflow.core.domain.cashflow

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplit
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.testing.Fixtures.tx
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/** All figures are fictional. */
class BufferTest {

    private val kindOf: (CategoryId) -> CategoryKind = { if (it == CategoryId.BUFFER || it == CategoryId.FUNDS) CategoryKind.SAVINGS else CategoryKind.VARIABLE }

    @Test
    fun `only money moved into Buffert counts, net of withdrawals, over every month`() {
        val rows = listOf(
            tx("1", date = "2026-05-26", kronor = -2000.0, category = CategoryId.BUFFER, categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.TO_SAVINGS),
            tx("2", date = "2026-06-26", kronor = -2000.0, category = CategoryId.BUFFER, categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.TO_SAVINGS),
            tx("3", date = "2026-06-28", kronor = 500.0, category = CategoryId.BUFFER, categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.FROM_SAVINGS),
            tx("4", date = "2026-06-26", kronor = -5000.0, category = CategoryId.FUNDS, categorizedBy = CategorizedBy.TRANSFER, transferKind = TransferKind.TO_SAVINGS),
        )
        assertThat(Buffer.value(FlowData.of(rows, MortgageSplit(), kindOf))).isEqualTo(Money.kr(3500))
        assertThat(Buffer.value(FlowData.of(rows.takeLast(1), MortgageSplit(), kindOf))).isNull()
        val data = FlowData.of(rows, MortgageSplit(), kindOf)
        assertThat(Buffer.movedIn(data, data.periods.takeLast(1))).isEqualTo(Money.kr(1500))
        assertThat(Buffer.months(Money.kr(3500), Money.kr(7000))).isEqualTo(0.5)
    }

    @Test
    fun `the recommended buffer is goal months of fixed costs, to the nearest hundred`() {
        assertThat(Buffer.target(6.0, Money(833_333))).isEqualTo(Money.kr(50000))
        assertThat(Buffer.target(3.0, Money(1_004_900))).isEqualTo(Money.kr(30100))
    }
}
