package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ParsedStatement
import app.cashflow.core.domain.model.StatementRow
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the engine decided for each file before anything is written. */
data class ImportPlan(
    val files: List<PlannedFile>,
) {
    val questions: List<AccountQuestion>
        get() = files.mapNotNull { it.guess as? AccountGuess.Ask }
            .groupBy { it.group }
            .map { (group, asks) ->
                AccountQuestion(
                    group = group,
                    fileNames = files.filter { (it.guess as? AccountGuess.Ask)?.group == group }.map { it.statement.fileName },
                    candidates = asks.flatMap { it.candidates }.distinct(),
                )
            }

    /** New accounts in savings exports whose type the name does not tell: asked once each. */
    val typeQuestions: List<NamedAccount>
        get() = files.flatMap { (it.guess as? AccountGuess.Named)?.accounts.orEmpty() }
            .filter { it.existing == null && it.type == null }
            .distinctBy { it.source.key }
}

data class PlannedFile(
    val statement: ParsedStatement,
    val guess: AccountGuess,
)

/** "Vilket konto?" — one question covers every file that chains together. */
data class AccountQuestion(
    val group: Int,
    val fileNames: List<String>,
    val candidates: List<AccountId>,
)

/** The user's answer to a question: an existing account, or a new one by name. */
sealed interface AccountAnswer {
    data class Existing(val accountId: AccountId) : AccountAnswer
    data class New(val account: Account) : AccountAnswer
}

data class ImportOutcome(
    val importIds: List<ImportId>,
    val newCount: Int,
    val duplicateCount: Int,
    val warningCount: Int,
)

/**
 * The import pipeline shared by file import and (later) bank sync:
 * read (done by the caller) → infer or create accounts → hash → insertBatch (atomic, skips
 * existing ids) → flag possible duplicates → match transfers → categorize. The receipt is
 * read back from storage by import id.
 */
class ImportStatementUseCase(
    private val transactions: TransactionLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val inferrer: AccountInferrer,
    private val hasher: TransactionHasher,
    private val normalizer: PayeeNormalizer,
    private val reclassify: ReclassifyUseCase,
    private val duplicates: DuplicateDetector = DuplicateDetector(normalizer),
    private val clock: Clock = Clock.System,
) {

    suspend fun plan(statements: List<ParsedStatement>): Result<ImportPlan, ImportError> {
        val nonEmpty = statements.filter { it.rows.isNotEmpty() }
        if (nonEmpty.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        val ordered = nonEmpty.map { it.copy(rows = RowOrder.oldestFirst(it.rows)) }
        val known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
        val states = (accounts.getBalanceStates() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)

        val (named, unnamed) = ordered.withIndex().partition { it.value.hasNamedAccounts }
        val guesses = inferrer.infer(
            inputs = unnamed.map { InferenceInput(it.index, it.value.rows) },
            // Savings accounts never chain with a bank statement.
            knownAccounts = known.filter { it.externalKey == null }.map { it.id },
            balanceStates = states,
            existingIds = { ids -> (transactions.existingIds(ids) as? Result.Success)?.data.orEmpty() },
        ).toMutableMap()
        for ((index, statement) in named) {
            val sources = statement.rows.mapNotNull { it.account }.distinctBy { it.key }
            guesses[index] = AccountGuess.Named(
                sources.map { source ->
                    val existing = known.firstOrNull { it.externalKey == source.key }
                    NamedAccount(source, existing?.id, existing?.type ?: AccountType.fromName(source.name))
                },
            )
        }
        return Result.Success(ImportPlan(ordered.mapIndexed { i, s -> PlannedFile(s, guesses.getValue(i)) }))
    }

    /**
     * [types] answers [ImportPlan.typeQuestions] by account key; an unanswered one becomes a
     * savings account.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(
        plan: ImportPlan,
        answers: Map<Int, AccountAnswer>,
        types: Map<String, AccountType> = emptyMap(),
    ): Result<ImportOutcome, ImportError> {
        val accountByGroup = HashMap<Int, Account>()
        val known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
        for ((group, answer) in answers) {
            val account = when (answer) {
                is AccountAnswer.Existing -> known.firstOrNull { it.id == answer.accountId } ?: return Result.Error(ImportError.UNKNOWN_ACCOUNT)
                is AccountAnswer.New -> answer.account.also {
                    if (accounts.upsertAccount(it) is Result.Error) return Result.Error(ImportError.STORAGE)
                }
            }
            accountByGroup[group] = account
        }
        val allAccounts = (known + accountByGroup.values.filter { a -> known.none { it.id == a.id } }).toMutableList()

        val ids = ArrayList<ImportId>()
        val newIds = ArrayList<app.cashflow.core.domain.model.TxId>()
        var newCount = 0
        var duplicates = 0
        var warnings = 0
        for (file in plan.files) {
            // Every row's account: one for a bank statement, one per named account in a savings export.
            val accountOfRow: (StatementRow) -> Account = when (val g = file.guess) {
                is AccountGuess.Known -> allAccounts.firstOrNull { it.id == g.accountId }?.let(::always) ?: return Result.Error(ImportError.UNKNOWN_ACCOUNT)
                is AccountGuess.Ask -> accountByGroup[g.group]?.let(::always) ?: return Result.Error(ImportError.UNKNOWN_ACCOUNT)
                is AccountGuess.Named -> {
                    val byKey = HashMap<String, Account>()
                    for (named in g.accounts) {
                        val account = allAccounts.firstOrNull { it.externalKey == named.source.key }
                            ?: Account(
                                id = AccountId(Uuid.random().toString()),
                                name = named.source.name,
                                type = named.type ?: types[named.source.key] ?: AccountType.SAVINGS,
                                institution = named.source.institution,
                                externalKey = named.source.key,
                            ).also {
                                if (accounts.upsertAccount(it) is Result.Error) return Result.Error(ImportError.STORAGE)
                                allAccounts += it
                            }
                        byKey[named.source.key] = account
                    }
                    val byRow: (StatementRow) -> Account = { row -> byKey.getValue(row.account!!.key) }
                    byRow
                }
            }

            val importId = ImportId(Uuid.random().toString())
            val rows = file.statement.rows
            val rowAccounts = rows.map(accountOfRow)
            // Hash per account, so identical rows without a balance are numbered within their own account.
            val txIds = arrayOfNulls<app.cashflow.core.domain.model.TxId>(rows.size)
            rows.indices.groupBy { rowAccounts[it].id }.forEach { (accountId, indexes) ->
                hasher.hashAll(accountId, indexes.map { rows[it] }).forEachIndexed { i, id -> txIds[indexes[i]] = id }
            }
            val txs = rows.mapIndexed { index, row ->
                val account = rowAccounts[index]
                Transaction(
                    id = txIds[index]!!,
                    accountId = account.id,
                    importId = importId,
                    bookingDate = row.bookingDate,
                    transactionDate = row.transactionDate,
                    amount = row.amount,
                    rawText = row.rawText,
                    normalizedText = normalizer.displayName(row.rawText),
                    balanceAfter = row.balanceAfter,
                    categoryId = CategoryId.UNCATEGORIZED,
                    categorizedBy = CategorizedBy.NONE,
                    transferKind = TransferKind.NONE,
                    fileOrder = index,
                    countsInCashFlow = account.type.countsInCashFlow,
                    movement = row.movement,
                )
            }.distinctBy { it.id }
            val fileAccounts = rowAccounts.map { it.id }.distinct()
            val batch = ImportBatch(
                id = importId,
                source = file.statement.source,
                fileName = file.statement.fileName,
                accountId = fileAccounts.singleOrNull(),
                importedAt = clock.now(),
                rowCount = rows.size,
                newCount = 0,
                duplicateCount = 0,
                warnings = file.statement.warnings,
            )
            val existingBefore = (transactions.existingIds(txs.map { it.id }) as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
            val summary = when (val r = transactions.insertBatch(batch, txs)) {
                is Result.Error -> return Result.Error(ImportError.STORAGE)
                is Result.Success -> r.data
            }
            accounts.addBalanceStates(
                rows.mapIndexedNotNull { i, row -> row.balanceAfter?.let { BalanceState(rowAccounts[i].id, row.bookingDate, it) } },
            )
            newIds += txs.map { it.id }.filter { it !in existingBefore }
            ids += importId
            newCount += summary.newCount
            duplicates += summary.duplicateCount
            warnings += file.statement.warnings.size
        }

        if (flagPossibleDuplicates(newIds.toSet()) is Result.Error) return Result.Error(ImportError.STORAGE)
        if (reclassify() is Result.Error) return Result.Error(ImportError.STORAGE)
        return Result.Success(ImportOutcome(ids, newCount, duplicates, warnings))
    }

    private suspend fun flagPossibleDuplicates(newIds: Set<app.cashflow.core.domain.model.TxId>): Result<Unit, *> {
        if (newIds.isEmpty()) return Result.Success(Unit)
        val all = (transactions.getAll() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
        val flagged = duplicates.find(all, newIds)
        if (flagged.isEmpty()) return Result.Success(Unit)
        return transactions.setDuplicates(flagged)
    }
}

/** Every row of a bank statement belongs to its one account. */
private fun always(account: Account): (StatementRow) -> Account = { account }
