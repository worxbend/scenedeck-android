pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "scenedeck-android"

include(":app")

include(":core:model")
include(":core:common")
include(":core:obs")
include(":core:data")
include(":core:datastore")
include(":core:database")
include(":core:designsystem")
include(":core:ui")

include(":feature:live")
include(":feature:mixer")
include(":feature:stats")
include(":feature:inventory")
include(":feature:graph")
include(":feature:doctor")
include(":feature:connections")
include(":feature:settings")
include(":feature:onboarding")
