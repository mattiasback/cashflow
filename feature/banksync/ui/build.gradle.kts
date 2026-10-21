import java.util.Properties

plugins {
    alias(libs.plugins.cashflow.android.feature)
}

// Your own Enable Banking application id, prefilled from the git-ignored local.properties.
// Temporary (step 9): goes away with the key import when a backend holds the key.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "ENABLE_BANKING_APP_ID", "\"${localProperties.getProperty("cashflow.enablebanking.appId", "")}\"")
    }
}

dependencies {
    implementation(projects.feature.banksync.domain)
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(projects.core.domain))
}
