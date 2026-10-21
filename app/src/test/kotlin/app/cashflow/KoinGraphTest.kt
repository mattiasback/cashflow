package app.cashflow

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/** Checks that every definition in the assembled graph can resolve its constructor parameters. */
class KoinGraphTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `koin graph is complete`() {
        module { includes(cashFlowModules) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                CoroutineScope::class,
                String::class,
                List::class,
                kotlin.time.Clock::class,
                Int::class,
                java.security.SecureRandom::class,
                io.ktor.client.engine.HttpClientEngine::class,
            ),
        )
    }
}
