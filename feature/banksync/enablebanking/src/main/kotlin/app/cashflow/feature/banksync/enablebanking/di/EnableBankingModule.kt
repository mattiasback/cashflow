package app.cashflow.feature.banksync.enablebanking.di

import app.cashflow.feature.banksync.domain.BankSyncProvider
import app.cashflow.feature.banksync.enablebanking.EnableBankingProvider
import app.cashflow.feature.banksync.enablebanking.JwtSigner
import app.cashflow.feature.banksync.enablebanking.enableBankingClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Binds Enable Banking as the provider. Another provider replaces this one module. */
val enableBankingModule = module {
    single(named("enablebanking")) { enableBankingClient() }
    single { JwtSigner() }
    single<BankSyncProvider> { EnableBankingProvider(get(), get(named("enablebanking")), get()) }
}
