package app.cashflow.feature.importer.ui

import app.cash.turbine.test
import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.testing.Fixtures.chain
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.AccountInferrer
import app.cashflow.core.importengine.ImportStatementUseCase
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransactionHasher
import app.cashflow.core.importengine.TransferMatcher
import app.cashflow.feature.importer.domain.ImportFilesUseCase
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementFileSource
import app.cashflow.feature.importer.domain.StatementReader
import app.cashflow.feature.importer.ui.entry.ImportAction
import app.cashflow.feature.importer.ui.entry.ImportEvent
import app.cashflow.feature.importer.ui.entry.ImportPhase
import app.cashflow.feature.importer.ui.entry.ImportViewModel
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Uses an in-memory file source: the system picker cannot be driven in tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val files = mapOf(
        "content://test/a" to ParsedStatement("a.xls", ImportSource.LANSFORSAKRINGAR_XLS, chain(100.0, Triple("2026-05-02", "ICA EXEMPEL", -50.0)), emptyList()),
    )
    private val source = object : StatementFileSource {
        override suspend fun open(uri: String) =
            if (uri in files) Result.Success(PickedFile(uri, ByteArray(1))) else Result.Error(ImportError.UNREADABLE_FILE)
    }
    private val reader = object : StatementReader {
        override fun read(file: PickedFile) = Result.Success(files.getValue(file.name))
    }
    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource()

    private fun viewModel(shared: List<String> = emptyList()): ImportViewModel {
        val normalizer = PayeeNormalizer()
        val hasher = TransactionHasher()
        val engine = ImportStatementUseCase(
            transactions, accounts, AccountInferrer(hasher), hasher, normalizer,
            ReclassifyUseCase(transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(), BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer)),
        )
        return ImportViewModel(shared, ImportFilesUseCase(source, reader, engine), accounts)
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `without shared files the picker opens`() = runTest {
        val vm = viewModel()
        vm.events.test { assertThat(awaitItem()).isEqualTo(ImportEvent.LaunchPicker) }
    }

    @Test
    fun `a first import asks which account, then shows the receipt`() = runTest {
        val vm = viewModel(shared = listOf("content://test/a"))
        assertThat(vm.state.value.phase).isEqualTo(ImportPhase.QUESTIONS)
        assertThat(vm.state.value.questions).hasSize(1)

        vm.events.test {
            vm.onAction(ImportAction.OnImportClick)
            assertThat(awaitItem()).isInstanceOf(ImportEvent.ShowReceipt::class)
        }
        assertThat(accounts.accounts.value.single().name).isEqualTo("Konto 1")
        assertThat(transactions.rows.value).hasSize(1)
    }

    @Test
    fun `an unreadable file fails with a clear error`() = runTest {
        val vm = viewModel(shared = listOf("content://test/missing"))
        assertThat(vm.state.value.phase).isEqualTo(ImportPhase.FAILED)
    }
}
