plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.koin)
    alias(libs.plugins.cashflow.kotlinx.serialization)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.categorization)
    implementation(libs.kermit)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.datastore)
}
