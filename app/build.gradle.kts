plugins {
    alias(libs.plugins.cashflow.android.application)
    alias(libs.plugins.cashflow.compose)
    alias(libs.plugins.cashflow.koin)
    alias(libs.plugins.cashflow.kotlinx.serialization)
}

android {
    namespace = "app.cashflow"
    defaultConfig {
        applicationId = "app.cashflow"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.importEngine)
    implementation(projects.core.demo)
    implementation(projects.core.categorization)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.designSystem)
    implementation(projects.feature.import.domain)
    implementation(projects.feature.import.data)
    implementation(projects.feature.import.ui)
    implementation(projects.feature.events.ui)
    implementation(projects.feature.flow.ui)
    implementation(projects.feature.home.ui)
    implementation(projects.feature.insights.ui)
    implementation(projects.feature.profile.ui)
    implementation(projects.feature.rules.ui)
    // Step 8, optional: bank sync through Enable Banking. The only modules that use the network.
    implementation(projects.feature.banksync.domain)
    implementation(projects.feature.banksync.enablebanking)
    implementation(projects.feature.banksync.data)
    implementation(projects.feature.banksync.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.compose.material3.navigationSuite)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.kermit)

    testImplementation(testFixtures(projects.core.domain))
    // KoinGraphTest names the HTTP engine, which the provider module builds itself.
    testImplementation(libs.ktor.client.core)
    androidTestImplementation(testFixtures(projects.core.domain))
    androidTestImplementation(platform(libs.koin.bom))
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI tests need an Espresso that knows API 36's InputManager.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
