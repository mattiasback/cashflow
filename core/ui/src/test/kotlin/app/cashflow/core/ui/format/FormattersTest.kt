package app.cashflow.core.ui.format

import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Period
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class FormattersTest {

    private val sv = AppLocale.SV
    private val en = AppLocale.EN

    @Test
    fun `money uses a no-break space and the real minus sign`() {
        assertThat(MoneyFormatter.format(Money.kr(18500), locale = sv)).isEqualTo("18 500 kr")
        assertThat(MoneyFormatter.format(Money.kr(-35000), locale = sv)).isEqualTo("−35 000 kr")
        assertThat(MoneyFormatter.format(Money.kr(1830), signed = true, locale = sv)).isEqualTo("+1 830 kr")
        assertThat(MoneyFormatter.format(Money(5485821), decimals = true, locale = sv)).isEqualTo("54 858,21 kr")
        assertThat(MoneyFormatter.format(Money.kr(12450), locale = en)).isEqualTo("12,450 kr")
        assertThat(MoneyFormatter.format(Money.kr(-12450), locale = en)).isEqualTo("−12,450 kr")
        assertThat(MoneyFormatter.format(Money.ZERO, signed = true, locale = sv)).isEqualTo("0 kr")
    }

    @Test
    fun `money rounds öre to whole kronor half away from zero`() {
        assertThat(MoneyFormatter.format(Money(-41250), locale = sv)).isEqualTo("−413 kr")
        assertThat(MoneyFormatter.format(Money(41249), locale = sv)).isEqualTo("412 kr")
    }

    @Test
    fun `talkback reads kronor`() {
        assertThat(MoneyFormatter.spoken(Money.kr(18500), sv)).isEqualTo("18 500 kronor")
        assertThat(MoneyFormatter.spoken(Money.kr(-1200), sv)).isEqualTo("minus 1 200 kronor")
    }

    @Test
    fun `percent has a no-break space in swedish and a signed variant`() {
        assertThat(PercentFormatter.format(12.5, locale = sv)).isEqualTo("12,5 %")
        assertThat(PercentFormatter.format(19.44, signed = true, locale = sv)).isEqualTo("+19,4 %")
        assertThat(PercentFormatter.format(-3.0, decimals = 0, locale = sv)).isEqualTo("−3 %")
        assertThat(PercentFormatter.format(12.5, locale = en)).isEqualTo("12.5%")
        assertThat(PercentFormatter.share(3, 1000, sv)).isEqualTo("<1 %")
        assertThat(PercentFormatter.share(140, 1000, sv)).isEqualTo("14 %")
    }

    @Test
    fun `periods and dates`() {
        assertThat(PeriodFormatter.month(Period(2026, 10), sv)).isEqualTo("okt 2026")
        assertThat(PeriodFormatter.month(Period(2026, 10), en)).isEqualTo("Oct 2026")
        assertThat(PeriodFormatter.iso(LocalDate(2026, 10, 27), sv)).isEqualTo("2026-10-27")
        assertThat(PeriodFormatter.dayHeading(LocalDate(2026, 10, 13), LocalDate(2026, 10, 15), sv)).isEqualTo("tis 13 okt")
    }
}
