package app.cashflow.feature.importer.data.di

import app.cashflow.feature.importer.data.ContentResolverFileSource
import app.cashflow.feature.importer.data.ProfileStatementReader
import app.cashflow.feature.importer.data.profile.AvanzaProfile
import app.cashflow.feature.importer.data.profile.LansforsakringarProfile
import app.cashflow.feature.importer.data.profile.LysaProfile
import app.cashflow.feature.importer.data.mapping.DefaultColumnMapper
import app.cashflow.feature.importer.domain.ColumnMapper
import app.cashflow.feature.importer.domain.ImportFilesUseCase
import app.cashflow.feature.importer.domain.StatementFileSource
import app.cashflow.feature.importer.domain.StatementReader
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val importDataModule = module {
    // Lambda form: the reader takes the list of supported bank profiles.
    single<StatementReader> { ProfileStatementReader(listOf(LansforsakringarProfile(), LysaProfile(), AvanzaProfile()), saved = get()) }
    single<ColumnMapper> { DefaultColumnMapper() }
    singleOf(::ContentResolverFileSource) { bind<StatementFileSource>() }
    factoryOf(::ImportFilesUseCase)
}
