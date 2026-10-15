package app.cashflow.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/** JVM 17 bytecode everywhere, regardless of the JDK Gradle runs on. */
fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        val options = compilerOptions
        if (options is KotlinJvmCompilerOptions) {
            options.jvmTarget.set(JvmTarget.JVM_17)
        }
        options.freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

/** Local unit tests run on the JUnit Platform (JUnit 5/6 Jupiter), AssertK, Turbine. */
fun Project.configureUnitTests() {
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
        add("testImplementation", libs.lib("assertk"))
        add("testImplementation", libs.lib("turbine"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Lets developers point the real-file tests at their own exports, which never enter git.
        System.getenv("CASHFLOW_PRIVATE_STATEMENTS")?.let { systemProperty("cashflow.privateStatements", it) }
        val privateDir = rootProject.file(".private/statements")
        if (privateDir.isDirectory) systemProperty("cashflow.privateStatements", privateDir.absolutePath)
        testLogging {
            events("failed")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
