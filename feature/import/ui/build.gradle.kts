plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    implementation(projects.feature.import.domain)
    implementation(libs.androidx.activity.compose)
    testImplementation(testFixtures(projects.core.domain))
}