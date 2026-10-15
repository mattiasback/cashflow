package app.cashflow.core.data.local

import app.cashflow.core.database.entity.AccountEntity
import app.cashflow.core.database.entity.BalanceStateEntity
import app.cashflow.core.database.entity.CategoryEntity
import app.cashflow.core.database.entity.ImportBatchEntity
import app.cashflow.core.database.entity.MerchantMemoryEntity
import app.cashflow.core.database.entity.RuleEntity
import app.cashflow.core.database.entity.TransactionEntity
import app.cashflow.core.domain.model.Account
import app.cashflow.core.domain.model.AccountId
import app.cashflow.core.domain.model.AccountType
import app.cashflow.core.domain.model.BalanceState
import app.cashflow.core.domain.model.CategorizedBy
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.ImportBatch
import app.cashflow.core.domain.model.ImportId
import app.cashflow.core.domain.model.ImportSource
import app.cashflow.core.domain.model.MerchantMemory
import app.cashflow.core.domain.model.Money
import app.cashflow.core.domain.model.RowWarning
import app.cashflow.core.domain.model.RowWarningReason
import app.cashflow.core.domain.model.RuleMatch
import app.cashflow.core.domain.model.Transaction
import app.cashflow.core.domain.model.TransferKind
import app.cashflow.core.domain.model.TxId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

fun TransactionEntity.toTransaction() = Transaction(
    id = TxId(id),
    accountId = AccountId(accountId),
    importId = ImportId(importId),
    bookingDate = LocalDate.fromEpochDays(bookingEpochDay),
    transactionDate = LocalDate.fromEpochDays(transactionEpochDay),
    amount = Money(amountOre),
    rawText = rawText,
    normalizedText = normalizedText,
    balanceAfter = balanceAfterOre?.let(::Money),
    categoryId = CategoryId(categoryId),
    categorizedBy = CategorizedBy.valueOf(categorizedBy),
    transferKind = TransferKind.valueOf(transferKind),
    transferPartnerId = transferPartnerId?.let(::TxId),
    loneLegConfirmed = loneLegConfirmed,
    fileOrder = fileOrder,
    countsInCashFlow = countsInCashFlow,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id.value,
    accountId = accountId.value,
    importId = importId.value,
    bookingEpochDay = bookingDate.toEpochDays(),
    transactionEpochDay = transactionDate.toEpochDays(),
    amountOre = amount.ore,
    rawText = rawText,
    normalizedText = normalizedText,
    balanceAfterOre = balanceAfter?.ore,
    categoryId = categoryId.value,
    categorizedBy = categorizedBy.name,
    transferKind = transferKind.name,
    transferPartnerId = transferPartnerId?.value,
    loneLegConfirmed = loneLegConfirmed,
    fileOrder = fileOrder,
    countsInCashFlow = countsInCashFlow,
)

fun AccountEntity.toAccount() = Account(AccountId(id), name, AccountType.valueOf(type), clearingPrefix, accountNumber)
fun Account.toEntity() = AccountEntity(id.value, name, type.name, clearingPrefix, accountNumber)

fun BalanceStateEntity.toBalanceState() = BalanceState(AccountId(accountId), LocalDate.fromEpochDays(epochDay), Money(balanceOre))
fun BalanceState.toEntity() = BalanceStateEntity(accountId.value, date.toEpochDays(), balance.ore)

fun CategoryEntity.toCategory() = Category(CategoryId(id), name, CategoryKind.valueOf(kind), sortOrder)
fun Category.toEntity() = CategoryEntity(id.value, name, kind.name, sortOrder)

fun RuleEntity.toRule() = CategoryRule(id, pattern, RuleMatch.valueOf(match), CategoryId(categoryId), sign, isBuiltIn = false)
fun CategoryRule.toEntity() = RuleEntity(id, pattern, match.name, categoryId.value, sign)

fun MerchantMemoryEntity.toMemory() = MerchantMemory(payeeKey, CategoryId(categoryId))
fun MerchantMemory.toEntity() = MerchantMemoryEntity(payeeKey, categoryId.value)

fun ImportBatchEntity.toBatch() = ImportBatch(
    id = ImportId(id),
    source = ImportSource.valueOf(source),
    fileName = fileName,
    accountId = accountId?.let(::AccountId),
    importedAt = Instant.fromEpochMilliseconds(importedAtEpochMillis),
    rowCount = rowCount,
    newCount = newCount,
    duplicateCount = duplicateCount,
    warnings = warnings.split(';').filter { it.isNotBlank() }.mapNotNull { part ->
        val (row, reason) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        RowWarning(row.toIntOrNull() ?: 0, runCatching { RowWarningReason.valueOf(reason) }.getOrNull() ?: return@mapNotNull null)
    },
)

fun ImportBatch.toEntity() = ImportBatchEntity(
    id = id.value,
    source = source.name,
    fileName = fileName,
    accountId = accountId?.value,
    importedAtEpochMillis = importedAt.toEpochMilliseconds(),
    rowCount = rowCount,
    newCount = newCount,
    duplicateCount = duplicateCount,
    warnings = warnings.joinToString(";") { "${it.rowNumber}:${it.reason.name}" },
)
