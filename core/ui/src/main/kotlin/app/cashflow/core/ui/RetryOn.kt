package app.cashflow.core.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen

/**
 * A screen's data that failed to load (a corrupt database, a full disk; expected failures are
 * typed Results) shows [onError], then waits for [retries] ("Försök igen") and reads again.
 * Nothing about the failure is logged: it may carry transaction data.
 */
fun <T> Flow<T>.retryOn(retries: Flow<Unit>, onError: () -> Unit, onRetry: () -> Unit): Flow<T> =
    retryWhen { cause, _ ->
        if (cause is CancellationException) return@retryWhen false
        onError()
        retries.first()
        onRetry()
        true
    }
