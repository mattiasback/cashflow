plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.koin)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.feature.import.domain)
    implementation(libs.kermit)
}