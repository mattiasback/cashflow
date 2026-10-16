package app.cashflow.feature.importer.data

import app.cashflow.feature.importer.domain.PickedFile
import kotlinx.coroutines.runBlocking

/** Reading is suspend (saved column mappings come from the database); tests read synchronously. */
fun ProfileStatementReader.readNow(file: PickedFile) = runBlocking { read(file) }
