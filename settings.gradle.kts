pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // <-- Add this
    }
}

rootProject.name = "EventsCollector"
include(":DemoApp")
include(":Annotations")
include(":Compiler")
include(":DataCollectorLib")
include(":Lint")
