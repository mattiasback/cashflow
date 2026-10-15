import app.cashflow.buildlogic.lib
import app.cashflow.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature `ui` module: Android library + Compose + Koin + Nav3 + the shared core UI modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("cashflow.android.library")
        pluginManager.apply("cashflow.compose")
        pluginManager.apply("cashflow.koin")
        pluginManager.apply("cashflow.kotlinx.serialization")
        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:design-system"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-savedstate"))
            add("implementation", libs.lib("navigation3-runtime"))
            add("implementation", libs.lib("kotlinx-datetime"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
