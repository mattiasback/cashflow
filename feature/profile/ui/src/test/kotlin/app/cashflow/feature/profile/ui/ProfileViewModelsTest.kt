package app.cashflow.feature.profile.ui

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.MortgageSplitMode
import app.cashflow.core.domain.model.Movement
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemorySettingsDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransferMatcher
import app.cashflow.feature.profile.ui.accounts.AccountSheetAction
import app.cashflow.feature.profile.ui.accounts.AccountSheetViewModel
import app.cashflow.feature.profile.ui.accounts.AccountsViewModel
import app.cashflow.feature.profile.ui.mortgage.MortgageAction
import app.cashflow.feature.profile.ui.mortgage.MortgageViewModel
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelsTest {

    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource()
    private val settings = InMemorySettingsDataSource()
    private val normalizer = PayeeNormalizer()
    private val reclassify = ReclassifyUseCase(
        transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.forLanguageTag("sv-SE"))
        accounts.accounts.value = listOf(
            Account(AccountId("bank"), "Lönekonto", AccountType.TRANSACTION),
            Account(AccountId("isk"), "ISK Exempel", AccountType.ISK, institution = "Avanza", externalKey = "avanza:ISK Exempel"),
            Account(AccountId("pen"), "Tjänstepension", AccountType.PENSION, institution = "Avanza", externalKey = "avanza:Tjänstepension"),
        )
        transactions.rows.update {
            listOf(
                tx("m1", "bank", "2026-09-28", -9000.0, "BOLÅN 1234"),
                tx("d1", "isk", "2026-05-02", 10000.0, countsInCashFlow = false, movement = Movement.DEPOSIT),
                tx("p1", "pen", "2026-05-02", 4000.0, countsInCashFlow = false, movement = Movement.DEPOSIT),
            ).associateBy { it.id }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `accounts split bank, savings and pension, and pension is not own savings`() = runTest {
        val vm = AccountsViewModel(accounts, transactions)
        val s = vm.state.value
        assertThat(s.bankAccounts.map { it.name }).isEqualTo(listOf("Lönekonto"))
        assertThat(s.savingsAccounts.map { it.name }).isEqualTo(listOf("ISK Exempel"))
        assertThat(s.pensionAccounts.map { it.name }).isEqualTo(listOf("Tjänstepension"))
        assertThat(s.savingsValue).isEqualTo("10\u00A0000\u00A0kr")
        assertThat(s.savingsNote).isNotNull()
    }

    @Test
    fun `a market value entered for an isk is saved with today's date`() = runTest {
        val vm = AccountSheetViewModel("isk", accounts, transactions, reclassify)
        assertThat(vm.state.value.showsMarketValue).isTrue()
        vm.onAction(AccountSheetAction.OnMarketValueChange("12 500,50"))
        vm.onAction(AccountSheetAction.OnSaveClick)
        val saved = accounts.accounts.value.first { it.id.value == "isk" }
        assertThat(saved.marketValue).isEqualTo(Money(1_250_050))
        assertThat(saved.marketValueDate).isNotNull()
    }

    @Test
    fun `the mortgage sheet previews the latest payment and saves the split`() = runTest {
        val vm = MortgageViewModel(settings, transactions)
        vm.onAction(MortgageAction.OnEnabledToggle(true))
        vm.onAction(MortgageAction.OnAmountChange("2500"))
        assertThat(vm.state.value.preview).isNotNull()
        vm.onAction(MortgageAction.OnSaveClick)
        val m = settings.state.value.mortgage
        assertThat(m.enabled).isTrue()
        assertThat(m.mode).isEqualTo(MortgageSplitMode.AMORTIZATION_AMOUNT)
        assertThat(m.amortization).isEqualTo(Money.kr(2500))
    }
}
