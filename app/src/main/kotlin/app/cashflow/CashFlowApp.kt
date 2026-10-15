package app.cashflow

import android.app.Application
import app.cashflow.core.categorization.di.categorizationModule
import app.cashflow.core.data.di.coreDataModule
import app.cashflow.core.importengine.di.importEngineModule
import app.cashflow.di.appModule
import app.cashflow.feature.events.ui.di.eventsUiModule
import app.cashflow.feature.flow.ui.flowUiModule
import app.cashflow.feature.importer.data.di.importDataModule
import app.cashflow.feature.importer.ui.importUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Every Koin module is assembled once, here. */
val cashFlowModules = listOf(
    coreDataModule,
    categorizationModule,
    importEngineModule,
    importDataModule,
    importUiModule,
    eventsUiModule,
    flowUiModule,
    appModule,
)

class CashFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@CashFlowApp)
            modules(cashFlowModules)
        }.koin
        koin.get<AppStartup>().run()
    }
}
