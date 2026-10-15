plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.compose)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.designSystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
