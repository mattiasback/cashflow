package app.cashflow.feature.banksync.data.di

import app.cashflow.feature.banksync.data.ContentKeyFileReader
import app.cashflow.feature.banksync.data.KeystoreCredentialStore
import app.cashflow.feature.banksync.data.RoomBankLinkDataSource
import app.cashflow.feature.banksync.domain.BankCredentialStore
import app.cashflow.feature.banksync.domain.BankLinkLocalDataSource
import app.cashflow.feature.banksync.domain.KeyFileReader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val bankSyncDataModule = module {
    single<BankCredentialStore> { KeystoreCredentialStore(androidContext()) }
    single<BankLinkLocalDataSource> { RoomBankLinkDataSource(get()) }
    factory<KeyFileReader> { ContentKeyFileReader(androidContext()) }
}
