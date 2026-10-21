plugins {
    alias(libs.plugins.cashflow.jvm.library)
}

dependencies {
    // A made-up household for demo mode, imported through the same pipeline as real files.
    api(projects.core.domain)
    implementation(projects.core.importEngine)
    implementation(projects.core.categorization)
    testImplementation(testFixtures(projects.core.domain))
}
