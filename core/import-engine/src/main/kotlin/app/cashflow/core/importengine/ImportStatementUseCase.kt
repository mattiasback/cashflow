package app.cashflow.core.importengine

import app.cashflow.core.categorization.PayeeNormalizer
import app.cashflow.core.domain.ImportError
import app.cashflow.core.domain.Result
import app.cashflow.core.domain.datasource.AccountLocalDataSource
import app.cashflow.core.domain.datasource.TransactionLocalDataSource
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ParsedStatement
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
 * read (done by the caller) → infer account → hash → insertBatch (atomic, skips existing
 * ids) → match transfers → categorize. The receipt is read back from storage by import id.
 */
class ImportStatementUseCase(
    private val transactions: TransactionLocalDataSource,
    private val accounts: AccountLocalDataSource,
    private val inferrer: AccountInferrer,
    private val hasher: TransactionHasher,
    private val normalizer: PayeeNormalizer,
    private val reclassify: ReclassifyUseCase,
    private val clock: Clock = Clock.System,
) {

    suspend fun plan(statements: List<ParsedStatement>): Result<ImportPlan, ImportError> {
        val nonEmpty = statements.filter { it.rows.isNotEmpty() }
        if (nonEmpty.isEmpty()) return Result.Error(ImportError.EMPTY_FILE)
        val ordered = nonEmpty.map { it.copy(rows = RowOrder.oldestFirst(it.rows)) }
        val known = (accounts.getAccounts() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
        val states = (accounts.getBalanceStates() as? Result.Success)?.data ?: return Result.Error(ImportError.STORAGE)
        val guesses = inferrer.infer(
            inputs = ordered.mapIndexed { i, s -> InferenceInput(i, s.rows) },
            knownAccounts = known.map { it.id },
            balanceStates = states,
            existingIds = { ids -> (transactions.existingIds(ids) as? Result.Success)?.data.orEmpty() },
        )
        return Result.Success(ImportPlan(ordered.mapIndexed { i, s -> PlannedFile(s, guesses.getValue(i)) }))
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(plan: ImportPlan, answers: Map<Int, AccountAnswer>): Result<ImportOutcome, ImportError> {
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
        val allAccounts = known + accountByGroup.values.filter { a -> known.none { it.id == a.id } }

        val ids = ArrayList<ImportId>()
        var newCount = 0
        var duplicates = 0
        var warnings = 0
        for (file in plan.files) {
            val account = when (val g = file.guess) {
                is AccountGuess.Known -> allAccounts.firstOrNull { it.id == g.accountId }
                is AccountGuess.Ask -> accountByGroup[g.group]
            } ?: return Result.Error(ImportError.UNKNOWN_ACCOUNT)

            val importId = ImportId(Uuid.random().toString())
            val rows = file.statement.rows
            val txIds = hasher.hashAll(account.id, rows)
            val txs = rows.mapIndexed { index, row ->
                Transaction(
                    id = txIds[index],
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
                )
            }.distinctBy { it.id }
            val batch = ImportBatch(
                id = importId,
                source = file.statement.source,
                fileName = file.statement.fileName,
                accountId = account.id,
                importedAt = clock.now(),
                rowCount = rows.size,
                newCount = 0,
                duplicateCount = 0,
                warnings = file.statement.warnings,
            )
            val summary = when (val r = transactions.insertBatch(batch, txs)) {
                is Result.Error -> return Result.Error(ImportError.STORAGE)
                is Result.Success -> r.data
            }
            accounts.addBalanceStates(rows.mapNotNull { row -> row.balanceAfter?.let { BalanceState(account.id, row.bookingDate, it) } })
            ids += importId
            newCount += summary.newCount
            duplicates += summary.duplicateCount
            warnings += file.statement.warnings.size
        }

        if (reclassify() is Result.Error) return Result.Error(ImportError.STORAGE)
        return Result.Success(ImportOutcome(ids, newCount, duplicates, warnings))
    }
}
