plugins {
    alias(libs.plugins.cashflow.android.feature)
}

dependencies {
    testImplementation(testFixtures(projects.core.domain))
}
