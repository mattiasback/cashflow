package app.cashflow.feature.banksync.domain

import app.cashflow.core.categorization.BundledCatalog
import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.TransactionOrigin
import app.cashflow.core.domain.model.TransactionStatus
import app.cashflow.core.domain.testing.Fixtures.tx
import app.cashflow.core.domain.testing.InMemoryAccountDataSource
import app.cashflow.core.domain.testing.InMemoryMerchantMemoryDataSource
import app.cashflow.core.domain.testing.InMemoryRuleDataSource
import app.cashflow.core.domain.testing.InMemoryTransactionDataSource
import app.cashflow.core.importengine.AccountAnswer
import app.cashflow.core.importengine.AccountInferrer
import app.cashflow.core.importengine.ImportStatementUseCase
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.ReconcilingInserter
import app.cashflow.core.importengine.SourceMatcher
import app.cashflow.core.importengine.TransactionHasher
import app.cashflow.core.importengine.TransferMatcher
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** A file import and bank sync of the same account never double any row. All data is fictional. */
class SyncBankUseCaseTest {

    private val account = Account(AccountId("acc-a"), "Lönekonto", AccountType.TRANSACTION)
    private val transactions = InMemoryTransactionDataSource()
    private val accounts = InMemoryAccountDataSource()
    private val normalizer = PayeeNormalizer()
    private val reclassify = ReclassifyUseCase(
        transactions, accounts, InMemoryMerchantMemoryDataSource(), InMemoryRuleDataSource(),
        BundledCatalog.load(), TransferMatcher(), Categorizer(normalizer),
    )
    private val provider = FakeProvider()
    private val links = InMemoryBankLinks()
    private val clock = object : kotlin.time.Clock {
        override fun now() = Instant.parse("2026-10-21T10:00:00Z")
    }
    private val sync = SyncBankUseCase(
        provider, links, accounts, transactions,
        ReconcilingInserter(transactions, SourceMatcher(normalizer)), BankRowMapper(normalizer), reclassify, clock,
    )
    private val today = LocalDate(2026, 10, 21)

    private val link = BankLink(
        stableKey = "hash-1", provider = "fake", accountId = account.id, institution = "Exempelbanken",
        sessionId = "s1", providerAccountUid = "uid-1", validUntil = Instant.parse("2027-04-01T00:00:00Z"),
        displayName = "Lönekonto", maskedNumber = "1234", lastSynced = null,
    )

    private fun bank(ref: String?, date: String, kronor: Double, text: String, booked: Boolean = true) =
        BankTransaction(ref, LocalDate.parse(date), null, Money.ofDouble(kronor), "SEK", text, null, booked)

    private suspend fun setUp() {
        accounts.upsertAccount(account)
        links.upsert(listOf(link))
        // Rows from an earlier xls import, with the file's own texts.
        transactions.rows.value = listOf(
            tx("f1", date = "2026-10-01", kronor = -100.0, text = "Kortköp 261001 MATBUTIK EXEMPEL", importId = "file"),
            tx("f2", date = "2026-10-05", kronor = -45.0, text = "Kortköp 261005 KAFE EXEMPEL", importId = "file")
                .copy(categoryId = CategoryId("mat"), categorizedBy = CategorizedBy.MANUAL),
        ).associateBy { it.id }
        provider.rows = listOf(
            bank("r3", "2026-10-08", -300.0, "BOKHANDEL EXEMPEL"),
            bank("r2", "2026-10-06", -45.0, "KAFE EXEMPEL"),
            bank("r1", "2026-10-01", -100.0, "MATBUTIK EXEMPEL"),
            bank(null, "2026-10-20", -59.0, "PENDING EXEMPEL", booked = false),
        )
    }

    @Test
    fun `rows the file already has are kept once, with their category`() = runTest {
        setUp()
        val outcome = (sync(today) as Result.Success).data

        assertThat(outcome.newCount).isEqualTo(1)
        assertThat(outcome.matchedCount).isEqualTo(2)
        val rows = transactions.rows.value.values
        assertThat(rows.size).isEqualTo(3)
        assertThat(rows.single { it.origin == TransactionOrigin.BANK }.rawText).isEqualTo("BOKHANDEL EXEMPEL")
        assertThat(transactions.rows.value.getValue(app.cashflow.core.domain.model.TxId("f2")).status).isEqualTo(TransactionStatus.MANUAL)
        // The first sync starts a week before the last file row.
        assertThat(provider.lastFrom).isEqualTo(LocalDate(2026, 9, 28))
    }

    @Test
    fun `a second sync adds nothing and starts a week before the first`() = runTest {
        setUp()
        sync(today)
        val again = (sync(today) as Result.Success).data

        assertThat(again.newCount).isEqualTo(0)
        assertThat(transactions.rows.value.size).isEqualTo(3)
        assertThat(provider.lastFrom).isEqualTo(LocalDate(2026, 10, 14))
    }

    @Test
    fun `a file imported after the sync is matched to the synced rows`() = runTest {
        setUp()
        sync(today)
        val hasher = TransactionHasher()
        val importer = ImportStatementUseCase(transactions, accounts, AccountInferrer(hasher), hasher, normalizer, reclassify)
        val file = ParsedStatement(
            "Kontoutdrag.xls", ImportSource.LANSFORSAKRINGAR_XLS,
            listOf(StatementRow(LocalDate(2026, 10, 8), LocalDate(2026, 10, 7), "Kortköp 261007 BOKHANDEL EXEMPEL", Money.ofDouble(-300.0), Money.ofDouble(5000.0))),
            emptyList(),
        )
        val plan = (importer.plan(listOf(file)) as Result.Success).data
        val outcome = (importer.execute(plan, plan.questions.associate { it.group to AccountAnswer.Existing(account.id) }) as Result.Success).data

        assertThat(outcome.newCount).isEqualTo(0)
        assertThat(outcome.matchedCount).isEqualTo(1)
        assertThat(transactions.rows.value.size).isEqualTo(3)
    }

    @Test
    fun `an expired consent asks to link the bank again`() = runTest {
        setUp()
        links.upsert(listOf(link.copy(validUntil = Instant.parse("2026-10-01T00:00:00Z"))))
        assertThat(sync(today)).isInstanceOf(Result.Error::class)
    }
}

private class FakeProvider : BankSyncProvider {
    override val id = "fake"
    override suspend fun checkCredentials(credentials: BankCredentials) = Result.Success(credentials)
    var rows: List<BankTransaction> = emptyList()
    var lastFrom: LocalDate? = null

    override suspend fun institutions(country: String) = Result.Success(listOf(BankInstitution("Exempelbanken", country, 180)))
    override suspend fun startAuthorization(institution: BankInstitution, validUntil: Instant, state: String) =
        Result.Success(AuthorizationStart("https://bank.example/auth?state=$state"))
    override suspend fun completeAuthorization(code: String) =
        Result.Success(BankConsent("s1", "Exempelbanken", Instant.parse("2027-04-01T00:00:00Z"), emptyList()))

    override suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate): Result<List<BankTransaction>, BankError> {
        lastFrom = from
        return Result.Success(rows.filter { from == null || it.bookingDate >= from })
    }

    override suspend fun revoke(sessionId: String) = Result.Success(Unit)
}

internal class InMemoryBankLinks : BankLinkLocalDataSource {
    val links = kotlinx.coroutines.flow.MutableStateFlow<Map<String, BankLink>>(emptyMap())
    override fun observeLinks() = kotlinx.coroutines.flow.flow { emit(links.value.values.toList()) }
    override suspend fun getLinks() = Result.Success(links.value.values.toList())
    override suspend fun upsert(links: List<BankLink>): Result<Unit, Nothing> {
        this.links.value = this.links.value + links.associateBy { it.stableKey }
        return Result.Success(Unit)
    }
    override suspend fun setLastSynced(stableKey: String, date: LocalDate): Result<Unit, Nothing> {
        links.value[stableKey]?.let { links.value = links.value + (stableKey to it.copy(lastSynced = date)) }
        return Result.Success(Unit)
    }
    override suspend fun deleteSession(sessionId: String): Result<Unit, Nothing> {
        links.value = links.value.filterValues { it.sessionId != sessionId }
        return Result.Success(Unit)
    }
}
