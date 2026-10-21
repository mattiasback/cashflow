package app.cashflow.di

import app.cashflow.AppStartup
import app.cashflow.RestartingModeController
import app.cashflow.core.demo.DemoSeeder
import app.cashflow.core.domain.model.AppModeController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {
    // Lambda forms: an application-wide scope is created, not constructed by injection.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<AppModeController> { RestartingModeController(androidContext(), get()) }
    factoryOf(::DemoSeeder)
    single { AppStartup(get(), get(), get(), get(), get(), get()) }
}
