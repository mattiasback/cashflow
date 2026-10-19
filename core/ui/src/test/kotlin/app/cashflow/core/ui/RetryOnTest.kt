package app.cashflow.core.ui

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RetryOnTest {

    @Test
    fun `a failure reports the error, waits for a retry, then reads again`() = runTest {
        var reads = 0
        val log = mutableListOf<String>()
        val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val source = flow {
            reads++
            if (reads == 1) error("database unreadable") else emit("data")
        }
        source.retryOn(retries, onError = { log += "error" }, onRetry = { log += "retry" }).test {
            // The first read failed: the screen shows the error and nothing is read again yet.
            testScheduler.runCurrent()
            assertThat(log).containsExactly("error")
            assertThat(reads).isEqualTo(1)
            retries.emit(Unit)
            assertThat(awaitItem()).isEqualTo("data")
            assertThat(log).containsExactly("error", "retry")
            awaitComplete()
        }
    }
}
