package app.cashflow.core.categorization.di

import app.cashflow.core.categorization.Categorizer
import app.cashflow.core.categorization.PayeeNormalizer
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val categorizationModule = module {
    singleOf(::PayeeNormalizer)
    singleOf(::Categorizer)
}
