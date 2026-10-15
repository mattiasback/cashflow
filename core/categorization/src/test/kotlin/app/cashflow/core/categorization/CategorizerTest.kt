package app.cashflow.core.categorization

import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.domain.model.TransferKind
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CategorizerTest {

    private val normalizer = PayeeNormalizer()
    private val categorizer = Categorizer(normalizer)

    @Test
    fun `normalizer strips processor prefixes, store numbers, city and country`() {
        assertThat(normalizer.normalize("SumUp  *KAFE EXEMPEL 1234,STAD,SE")).isEqualTo("KAFE EXEMPEL")
        assertThat(normalizer.normalize("Zettle_*Glassbaren,Ort,SE")).isEqualTo("Glassbaren")
        assertThat(normalizer.normalize("K*webbshop.se")).isEqualTo("webbshop.se")
        assertThat(normalizer.normalize("ICA NARA EXEMPEL,STAD,SE")).isEqualTo("ICA NARA EXEMPEL")
        assertThat(normalizer.normalize("MATBUTIK   CITY   #4411")).isEqualTo("MATBUTIK CITY")
        assertThat(normalizer.displayName("WILLYS EXEMPELSTAD,STAD,SE")).isEqualTo("Willys Exempelstad")
    }

    @Test
    fun `pipeline order is transfer, memory, user rule, built-in rule, uncategorized`() = runTest {
        val builtIn = BundledCatalog.load().builtInRules()
        val user = listOf(CategoryRule("u1", "ICA", RuleMatch.CONTAINS, CategoryId("shopping"), isBuiltIn = false))
        val memory = listOf(MerchantMemory("ICA NARA EXEMPEL", CategoryId("restaurang")))
        val minus = Money.kr(-100)

        fun decide(text: String, kind: TransferKind = TransferKind.NONE, ctx: CategorizationContext) =
            categorizer.categorize(text, minus, kind, loneLegConfirmed = false, context = ctx)

        val all = CategorizationContext(memory, user, builtIn)
        assertThat(decide("ICA NARA EXEMPEL,STAD,SE", TransferKind.INTERNAL, all).categorizedBy).isEqualTo(CategorizedBy.TRANSFER)
        assertThat(decide("ICA NARA EXEMPEL,STAD,SE", ctx = all)).isEqualTo(CategoryDecision(CategoryId("restaurang"), CategorizedBy.MEMORY))
        assertThat(decide("ICA KVANTUM ANNAN,STAD,SE", ctx = all)).isEqualTo(CategoryDecision(CategoryId("shopping"), CategorizedBy.USER_RULE))
        val builtInOnly = CategorizationContext(emptyList(), emptyList(), builtIn)
        assertThat(decide("ICA KVANTUM ANNAN,STAD,SE", ctx = builtInOnly)).isEqualTo(CategoryDecision(CategoryId("mat"), CategorizedBy.BUILT_IN_RULE))
        assertThat(decide("Swish till Okänd", ctx = builtInOnly)).isEqualTo(CategoryDecision(CategoryId.UNCATEGORIZED, CategorizedBy.NONE))
    }

    @Test
    fun `contains matches whole words only and longer patterns win`() = runTest {
        val ctx = CategorizationContext(emptyList(), emptyList(), BundledCatalog.load().builtInRules())
        fun cat(text: String, kr: Long = -100) = categorizer.categorize(text, Money.kr(kr), TransferKind.NONE, false, ctx).categoryId.value

        assertThat(cat("AFRICA SHOP")).isEqualTo("okat")
        assertThat(cat("ICA FÖRSÄKRING")).isEqualTo("forsakringar")
        assertThat(cat("MAXI ICA STORMARKNAD,STAD,SE")).isEqualTo("mat")
        assertThat(cat("CENTRALA STUDIESTÖDSNÄMNDEN")).isEqualTo("lan")
        assertThat(cat("CENTRALA STUDIESTÖDSNÄMNDEN", kr = 3000)).isEqualTo("ersattning")
    }
}
