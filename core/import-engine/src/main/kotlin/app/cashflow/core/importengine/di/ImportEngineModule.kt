package app.cashflow.core.importengine.di

import app.cashflow.core.importengine.AccountInferrer
import app.cashflow.core.importengine.CategorizeUseCase
import app.cashflow.core.importengine.HideDeleteUseCase
import app.cashflow.core.importengine.UndoImportUseCase
import app.cashflow.core.importengine.ImportStatementUseCase
import app.cashflow.core.importengine.ReclassifyUseCase
import app.cashflow.core.importengine.TransactionHasher
import app.cashflow.core.importengine.TransferMatcher
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val importEngineModule = module {
    singleOf(::TransactionHasher)
    singleOf(::AccountInferrer)
    single { TransferMatcher() }
    factoryOf(::ReclassifyUseCase)
    factoryOf(::CategorizeUseCase)
    // Lambda forms: the clock keeps its default.
    factory { UndoImportUseCase(get(), get()) }
    factory { HideDeleteUseCase(get(), get()) }
    factory {
        ImportStatementUseCase(
            transactions = get(),
            accounts = get(),
            inferrer = get(),
            hasher = get(),
            normalizer = get(),
            reclassify = get(),
        )
    }
}
