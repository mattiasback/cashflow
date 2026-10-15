plugins {
    alias(libs.plugins.cashflow.android.library)
    alias(libs.plugins.cashflow.room)
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.assertk)
}
