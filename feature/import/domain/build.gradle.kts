plugins {
    alias(libs.plugins.cashflow.jvm.library)
}

dependencies {
    api(projects.core.domain)
    // The import engine is pure domain logic shared with (later) bank sync.
    api(projects.core.importEngine)
}
