plugins {
    alias(libs.plugins.cashflow.android.feature)
    alias(libs.plugins.screenshot)
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    // The scroll performance test runs against release code (not debuggable), as on a phone.
    testBuildType = providers.gradleProperty("flowPerfRelease").map { "release" }.getOrElse("debug")
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("debug")
}

dependencies {
    testImplementation(testFixtures(projects.core.domain))
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(testFixtures(projects.core.domain))
    screenshotTestImplementation(libs.compose.ui.tooling)
    androidTestImplementation(testFixtures(projects.core.domain))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Library tests run in their own APK, so the test activity's manifest goes there only.
    androidTestImplementation(libs.compose.ui.test.manifest)
}
