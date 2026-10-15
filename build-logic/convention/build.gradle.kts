import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "app.cashflow.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "cashflow.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "cashflow.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "cashflow.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "cashflow.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("compose") {
            id = "cashflow.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("koin") {
            id = "cashflow.koin"
            implementationClass = "KoinConventionPlugin"
        }
        register("room") {
            id = "cashflow.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("kotlinxSerialization") {
            id = "cashflow.kotlinx.serialization"
            implementationClass = "KotlinxSerializationConventionPlugin"
        }
    }
}
