package app.cashflow.core.ui

import app.cashflow.core.domain.model.DebugFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
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

/**
 * The debug switches (Profil › Utvecklare) on a tab's data: "Långsam laddning" holds each read
 * back three seconds, "Simulera läsfel" fails it, which [retryOn] then turns into "Försök igen".
 * Only the tabs with skeletons and a retry use it; with both off the data passes straight through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.withDebugFlags(flags: DebugFlags): Flow<T> {
    val source = this
    return combine(flags.slowLoading, flags.failReads) { slow, fail -> slow to fail }.flatMapLatest { (slow, fail) ->
        when {
            fail -> flow<T> {
                if (slow) delay(DEBUG_DELAY_MS)
                throw IllegalStateException("Simulated read error (debug)")
            }
            slow -> source.onStart { delay(DEBUG_DELAY_MS) }
            else -> source
        }
    }
}

private const val DEBUG_DELAY_MS = 3_000L
