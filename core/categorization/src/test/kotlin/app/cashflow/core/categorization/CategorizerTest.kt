package app.cashflow.core.categorization

import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.Movement
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

    @Test
    fun `rows inside savings are categorized by their movement and never by rules`() = runTest {
        val ctx = CategorizationContext(emptyList(), emptyList(), BundledCatalog.load().builtInRules())
        fun decide(movement: Movement) = categorizer.categorize(
            CategorizationInput("ICA Utdelning", Money.kr(100), TransferKind.NONE, loneLegConfirmed = false, countsInCashFlow = false, movement = movement),
            ctx,
        )
        assertThat(decide(Movement.TRADE)).isEqualTo(CategoryDecision(CategoryId.TRADES, CategorizedBy.SAVINGS_EXPORT))
        assertThat(decide(Movement.RETURN)).isEqualTo(CategoryDecision(CategoryId.SAVINGS_RETURN, CategorizedBy.SAVINGS_EXPORT))
        assertThat(decide(Movement.COST)).isEqualTo(CategoryDecision(CategoryId.SAVINGS_COST, CategorizedBy.SAVINGS_EXPORT))
        assertThat(decide(Movement.DEPOSIT).categoryId).isEqualTo(CategoryId.TRANSFER)
    }

    @Test
    fun `a savings transfer takes the category of its savings account, else a savings rule`() = runTest {
        val ctx = CategorizationContext(emptyList(), emptyList(), BundledCatalog.load().builtInRules())
        fun decide(text: String, savingsCategory: CategoryId?) = categorizer.categorize(
            CategorizationInput(text, Money.kr(-500), TransferKind.TO_SAVINGS, loneLegConfirmed = false, savingsCategory = savingsCategory),
            ctx,
        ).categoryId
        assertThat(decide("Lysa Spar", CategoryId.BUFFER)).isEqualTo(CategoryId.BUFFER)
        assertThat(decide("Lysa Spar", null)).isEqualTo(CategoryId.FUNDS)
        assertThat(decide("ICA", null)).isEqualTo(CategoryId.SAVINGS_TRANSFER)
    }

    @Test
    fun `a user rule can be limited to an account and an amount`() = runTest {
        val rule = CategoryRule(
            "u1", "BOLÅN", RuleMatch.STARTS_WITH, CategoryId("boende"), isBuiltIn = false,
            accountId = AccountId("bank-a"), minAmount = Money.kr(5000),
        )
        val ctx = CategorizationContext(emptyList(), listOf(rule), emptyList())
        fun decide(amount: Long, account: String) = categorizer.categorize(
            CategorizationInput("BOLÅN 123", Money.kr(amount), TransferKind.NONE, loneLegConfirmed = false, accountId = AccountId(account)),
            ctx,
        ).categoryId
        assertThat(decide(-8000, "bank-a")).isEqualTo(CategoryId("boende"))
        assertThat(decide(-800, "bank-a")).isEqualTo(CategoryId.UNCATEGORIZED)
        assertThat(decide(-8000, "bank-b")).isEqualTo(CategoryId.UNCATEGORIZED)
    }
}
