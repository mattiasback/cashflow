plugins {
    alias(libs.plugins.cashflow.android.feature)
    alias(libs.plugins.screenshot)
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    testImplementation(testFixtures(projects.core.domain))
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(testFixtures(projects.core.domain))
    screenshotTestImplementation(libs.compose.ui.tooling)
}
