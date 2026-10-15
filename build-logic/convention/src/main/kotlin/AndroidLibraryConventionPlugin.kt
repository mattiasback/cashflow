import app.cashflow.buildlogic.configureKotlinJvmTarget
import app.cashflow.buildlogic.configureUnitTests
import app.cashflow.buildlogic.int
import app.cashflow.buildlogic.lib
import app.cashflow.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = "app.cashflow." + path.removePrefix(":").replace(':', '.').replace('-', '_')
            compileSdk = libs.int("compileSdk")
            defaultConfig {
                minSdk = libs.int("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests.isReturnDefaultValues = true
            }
        }
        configureKotlinJvmTarget()
        configureUnitTests()
        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-core"))
        }
    }
}
