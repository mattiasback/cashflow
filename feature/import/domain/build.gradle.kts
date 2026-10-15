plugins {
    alias(libs.plugins.cashflow.jvm.library)
}

dependencies {
    api(projects.core.domain)
}