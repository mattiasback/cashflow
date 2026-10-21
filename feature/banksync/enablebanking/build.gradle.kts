plugins {
    alias(libs.plugins.cashflow.jvm.library)
    alias(libs.plugins.cashflow.kotlinx.serialization)
    alias(libs.plugins.cashflow.koin)
}

// The only module that talks to the network: Enable Banking's read-only account API.
dependencies {
    implementation(projects.feature.banksync.domain)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kermit)

    testImplementation(libs.ktor.client.mock)
}
