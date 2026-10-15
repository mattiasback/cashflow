plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.koin)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.feature.import.domain)
    implementation(libs.kermit)

    // The private-data acceptance test runs the whole pipeline on the real exports.
    testImplementation(projects.core.importEngine)
    testImplementation(projects.core.categorization)
    testImplementation(testFixtures(projects.core.domain))
}
