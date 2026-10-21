package app.cashflow.feature.banksync.domain.di

import app.cashflow.core.importengine.ReconcilingInserter
import app.cashflow.core.importengine.SourceMatcher
import app.cashflow.feature.banksync.domain.BankKeyUseCase
import app.cashflow.feature.banksync.domain.BankRowMapper
import app.cashflow.feature.banksync.domain.ConnectBankUseCase
import app.cashflow.feature.banksync.domain.DisconnectBankUseCase
import app.cashflow.feature.banksync.domain.LinkAccountsUseCase
import app.cashflow.feature.banksync.domain.SyncBankUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Provider-neutral bank sync. The provider itself is bound by its own module. */
val bankSyncDomainModule = module {
    factory { BankRowMapper(get()) }
    factory { ReconcilingInserter(get(), SourceMatcher(get())) }
    // Lambda forms: the clock and the random source keep their defaults.
    factory { ConnectBankUseCase(get(), get()) }
    factoryOf(::LinkAccountsUseCase)
    factoryOf(::DisconnectBankUseCase)
    factoryOf(::BankKeyUseCase)
    factory { SyncBankUseCase(get(), get(), get(), get(), get(), get(), get()) }
}
