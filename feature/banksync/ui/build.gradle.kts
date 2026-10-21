plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    implementation(projects.feature.banksync.domain)
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(projects.core.domain))
}
