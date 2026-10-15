plugins {
    alias(libs.plugins.cashflow.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.datetime)
}
