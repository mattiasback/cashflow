import app.cashflow.buildlogic.lib
import app.cashflow.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Koin for any module; Android modules also get the Android and Compose artifacts. */
class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("implementation", platform(libs.lib("koin-bom")))
            add("implementation", libs.lib("koin-core"))
            add("testImplementation", platform(libs.lib("koin-bom")))
            add("testImplementation", libs.lib("koin-test"))
            add("testImplementation", libs.lib("koin-test-junit5"))
        }
        pluginManager.withPlugin("com.android.base") {
            dependencies {
                add("implementation", libs.lib("koin-android"))
                add("implementation", libs.lib("koin-androidx-compose"))
            }
        }
    }
}
