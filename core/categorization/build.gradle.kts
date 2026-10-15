plugins {
    alias(libs.plugins.cashflow.jvm.library)
    alias(libs.plugins.cashflow.koin)
    alias(libs.plugins.cashflow.kotlinx.serialization)
}

dependencies {
    api(projects.core.domain)
    testImplementation(testFixtures(projects.core.domain))
}