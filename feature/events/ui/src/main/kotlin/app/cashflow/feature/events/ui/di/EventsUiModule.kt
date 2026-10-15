package app.cashflow.feature.events.ui.di

import app.cashflow.feature.events.ui.detail.TransactionDetailViewModel
import app.cashflow.feature.events.ui.list.EventsViewModel
import app.cashflow.feature.events.ui.review.ReviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val eventsUiModule = module {
    // Lambda forms: these take navigation arguments as parameters.
    viewModel { params ->
        EventsViewModel(get(), get(), get(), params.values.getOrNull(0) as String?, params.values.getOrNull(1) as String?)
    }
    viewModelOf(::ReviewViewModel)
    viewModel { params -> TransactionDetailViewModel(params.get(), get(), get(), get(), get()) }
}
