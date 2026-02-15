rootProject.name = "MinAndroidExample"
include(":shared")
include(":app")

pluginManagement {
    repositories {
        google()            // <--- Add this first
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()            // <--- Also ensure it's here for dependencies
        mavenCentral()
    }
}