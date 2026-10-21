plugins {
    alias(libs.plugins.cashflow.jvm.library)
    alias(libs.plugins.cashflow.koin)
}

dependencies {
    api(projects.core.domain)
    // Synced rows go through the same matching, transfer and category steps as file rows.
    api(projects.core.importEngine)
    implementation(projects.core.categorization)

    testImplementation(testFixtures(projects.core.domain))
}
