plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    implementation(projects.core.importEngine)
    testImplementation(testFixtures(projects.core.domain))
    testImplementation(projects.core.categorization)
}
