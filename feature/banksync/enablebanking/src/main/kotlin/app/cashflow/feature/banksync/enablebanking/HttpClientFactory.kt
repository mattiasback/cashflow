package app.cashflow.feature.banksync.enablebanking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val HOST = "api.enablebanking.com"

/** Refuses any request that is not HTTPS to Enable Banking, whatever a caller builds. */
private val OnlyEnableBanking = createClientPlugin("OnlyEnableBanking") {
    onRequest { request, _ ->
        check(request.url.protocol == URLProtocol.HTTPS && request.url.host == HOST) { "Bank sync only talks to $HOST" }
    }
}

internal val enableBankingJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

internal fun HttpClientConfig<*>.configureEnableBanking() {
    expectSuccess = false
    install(OnlyEnableBanking)
    install(ContentNegotiation) { json(enableBankingJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
}

/** No logging plugin: requests carry your token and answers carry account data. */
fun enableBankingClient(): HttpClient = HttpClient(OkHttp) { configureEnableBanking() }

internal fun enableBankingClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configureEnableBanking() }
