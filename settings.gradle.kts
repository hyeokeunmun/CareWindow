pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroup("ai.pleos.playground") }
        }
    }
}
rootProject.name = "CareWindow"
include(":core")
// JVM tests can run without an installed Android SDK: ./gradlew -PcoreOnly :core:test
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
