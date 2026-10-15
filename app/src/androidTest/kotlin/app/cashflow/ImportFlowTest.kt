package app.cashflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.CategoryLocalDataSource
import app.cashflow.core.domain.datasource.ImportBatchLocalDataSource
import app.cashflow.core.domain.datasource.MerchantMemoryLocalDataSource
import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.datasource.RuleLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.testing.Fixtures.chain
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryCategoryDataSource
import app.cashflow.core.domain.testing.InMemoryImportBatchDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.feature.importer.domain.PickedFile
import app.cashflow.feature.importer.domain.StatementFileSource
import app.cashflow.feature.importer.domain.StatementReader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * import → receipt → review → Händelser, end to end through the real screens and engine,
 * with in-memory storage and an in-memory file source (the system picker cannot be driven).
 * Fictional data only.
 */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val statement = ParsedStatement(
        fileName = "Exempel.xls",
        source = ImportSource.LANSFORSAKRINGAR_XLS,
        rows = chain(
            1000.0,
            Triple("2026-05-02", "MATBUTIK ICA EXEMPEL,STAD,SE", -250.0),
            Triple("2026-05-03", "Swish till Testperson", -120.0),
            Triple("2026-05-25", "LÖN", 20000.0),
        ),
        warnings = emptyList(),
    )

    private val testDataModule = module {
        val batches = InMemoryImportBatchDataSource()
        single<TransactionLocalDataSource> { InMemoryTransactionDataSource(batches) }
        single<ImportBatchLocalDataSource> { batches }
        single<AccountLocalDataSource> { InMemoryAccountDataSource() }
        single<CategoryLocalDataSource> { InMemoryCategoryDataSource(runBlocking { BundledCatalog.load().defaultCategories() }) }
        single<RuleLocalDataSource> { InMemoryRuleDataSource() }
        single<MerchantMemoryLocalDataSource> { InMemoryMerchantMemoryDataSource() }
        single<RuleCatalog> { BundledCatalog.load() }
        single<StatementFileSource> {
            object : StatementFileSource {
                override suspend fun open(uri: String) = Result.Success(PickedFile("Exempel.xls", ByteArray(1)))
            }
        }
        single<StatementReader> {
            object : StatementReader {
                override fun read(file: PickedFile): Result<ParsedStatement, ImportError> = Result.Success(statement)
            }
        }
    }

    @Before
    fun setUp() = loadKoinModules(testDataModule)

    @After
    fun tearDown() = unloadKoinModules(testDataModule)

    @Test
    fun importReceiptReviewAndEvents() {
        val shares = MutableSharedFlow<List<String>>(replay = 1)
        compose.setContent { CashFlowTheme(darkTheme = false) { CashFlowAppShell(sharedUris = shares) } }
        ImportFlowRobot(compose)
            .assertFlowEmptyState()
            .shareFile(shares)
            .answerNewAccount()
            .assertReceipt(newRows = 3)
            .openReview()
            .approveFirstGroup("Nöje")
            .assertReviewDone()
    }
}

class ImportFlowRobot(private val compose: ComposeContentTestRule) {

    fun assertFlowEmptyState() = apply {
        compose.onNodeWithText("Inget flöde än").assertIsDisplayed()
    }

    fun shareFile(shares: MutableSharedFlow<List<String>>) = apply {
        shares.tryEmit(listOf("content://test/exempel"))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Vilket konto gäller filerna?").fetchSemanticsNodes().isNotEmpty() }
    }

    fun answerNewAccount() = apply {
        compose.onNodeWithText("Importera").performClick()
    }

    fun assertReceipt(newRows: Int) = apply {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Import klar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("$newRows nya transaktioner", substring = true).assertIsDisplayed()
    }

    fun openReview() = apply {
        compose.onNodeWithText("Granska nu").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Godkänn").fetchSemanticsNodes().isNotEmpty() }
    }

    fun approveFirstGroup(category: String) = apply {
        compose.onAllNodesWithText(category).onFirst().performClick()
        compose.onAllNodesWithText("Godkänn").onFirst().performClick()
    }

    fun assertReviewDone() = apply {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Allt är kategoriserat").fetchSemanticsNodes().isNotEmpty() }
    }

    @Suppress("unused")
    fun scrollTo(text: String) = apply { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text)) }
}
