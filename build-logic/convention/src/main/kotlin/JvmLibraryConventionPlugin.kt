import app.cashflow.buildlogic.configureKotlinJvmTarget
import app.cashflow.buildlogic.configureUnitTests
import app.cashflow.buildlogic.lib
import app.cashflow.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin module (domain-module in the skill): no Android dependencies. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlinJvmTarget()
        configureUnitTests()
        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-core"))
        }
    }
}
