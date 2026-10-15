import androidx.room.gradle.RoomExtension
import app.cashflow.buildlogic.lib
import app.cashflow.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")
        extensions.configure<RoomExtension> {
            // The schema is exported to git from the first version so migrations can be tested.
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("room-runtime"))
            add("implementation", libs.lib("room-ktx"))
            add("ksp", libs.lib("room-compiler"))
            add("androidTestImplementation", libs.lib("room-testing"))
        }
    }
}
