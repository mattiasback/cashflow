plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    implementation(projects.feature.import.domain)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation3.ui)

    testImplementation(testFixtures(projects.core.domain))
    testImplementation(projects.core.categorization)
}
