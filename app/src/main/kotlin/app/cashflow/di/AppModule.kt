package app.cashflow.di

import app.cashflow.AppStartup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {
    // Lambda forms: an application-wide scope is created, not constructed by injection.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AppStartup(get(), get(), get()) }
}
