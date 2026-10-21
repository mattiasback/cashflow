pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CashFlow"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:domain")
include(":core:import-engine")
include(":core:categorization")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:design-system")
include(":core:demo")
include(":feature:import:domain")
include(":feature:import:data")
include(":feature:import:ui")
include(":feature:events:ui")
include(":feature:flow:ui")
include(":feature:home:ui")
include(":feature:insights:domain")
include(":feature:insights:ui")
include(":feature:profile:ui")
include(":feature:rules:ui")
