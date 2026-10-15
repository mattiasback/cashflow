plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    // Review and re-categorization use cases are shared engine logic.
    implementation(projects.core.importEngine)
    implementation(libs.navigation3.ui)

    testImplementation(testFixtures(projects.core.domain))
    testImplementation(projects.core.categorization)
}
