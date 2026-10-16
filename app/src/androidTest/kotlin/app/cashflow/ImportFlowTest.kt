package app.cashflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
                override suspend fun read(file: PickedFile): Result<ParsedStatement, ImportError> = Result.Success(statement)
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
            .approveFirstGroup(app.cashflow.core.ui.R.string.category_noje)
            .assertReviewDone()
    }
}

class ImportFlowRobot(private val compose: ComposeContentTestRule) {

    // Strings come from resources so the test runs in Swedish and English.
    private val res = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
    private fun s(id: Int) = res.getString(id)

    fun assertFlowEmptyState() = apply {
        compose.onNodeWithText(s(app.cashflow.feature.flow.ui.R.string.flow_empty_title)).assertIsDisplayed()
    }

    fun shareFile(shares: MutableSharedFlow<List<String>>) = apply {
        shares.tryEmit(listOf("content://test/exempel"))
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(app.cashflow.feature.importer.ui.R.string.import_which_account)).fetchSemanticsNodes().isNotEmpty() }
    }

    fun answerNewAccount() = apply {
        compose.onNodeWithText(s(app.cashflow.feature.importer.ui.R.string.import_button_go)).performClick()
    }

    fun assertReceipt(newRows: Int) = apply {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(app.cashflow.feature.importer.ui.R.string.receipt_title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(res.getQuantityString(app.cashflow.feature.importer.ui.R.plurals.receipt_new, newRows, newRows)).assertIsDisplayed()
    }

    fun openReview() = apply {
        // The Händelser review card behind the sheet has the same label; the sheet is drawn last.
        compose.onAllNodesWithText(s(app.cashflow.feature.importer.ui.R.string.receipt_review_now)).onLast().performClick()
        // Before a category is picked the approve button reads "Välj en kategori först".
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(app.cashflow.feature.events.ui.R.string.review_pick_first)).fetchSemanticsNodes().isNotEmpty() }
    }

    fun approveFirstGroup(categoryRes: Int) = apply {
        compose.onAllNodesWithText(s(categoryRes)).onFirst().performClick()
        compose.onAllNodesWithText(s(app.cashflow.feature.events.ui.R.string.review_approve)).onFirst().performClick()
    }

    fun assertReviewDone() = apply {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(s(app.cashflow.feature.events.ui.R.string.review_done_title)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Suppress("unused")
    fun scrollTo(text: String) = apply { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text)) }
}
