plugins {
    alias(libs.plugins.cashflow.jvm.library)
}

dependencies {
    // Detectors, the plan and the what-if are pure functions of the household's rows and sums.
    api(projects.core.domain)
    testImplementation(testFixtures(projects.core.domain))
}
