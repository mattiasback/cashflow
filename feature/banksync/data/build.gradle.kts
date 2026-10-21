plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.koin)
    alias(libs.plugins.cashflow.kotlinx.serialization)
}

dependencies {
    implementation(projects.feature.banksync.domain)
    implementation(projects.core.database)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kermit)
}
