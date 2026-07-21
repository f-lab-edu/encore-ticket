pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "ticket"

include(
    "app",
    "auth",
    "catalog",
    "booking",
    "payment",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")