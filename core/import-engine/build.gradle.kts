plugins {
    alias(libs.plugins.cashflow.jvm.library)
    alias(libs.plugins.cashflow.koin)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.categorization)
    testImplementation(testFixtures(projects.core.domain))
}