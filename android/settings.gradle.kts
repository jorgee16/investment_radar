pluginManagement {
    repositories {
        // CI-only, JetBrains-operated cache redirector for Kotlin artifacts.
        // Avoid Maven Central's GitHub-release redirect during runner HTTP 504.
        // Normal developer builds keep their original repository order.
        if (System.getenv("IR_CI_KOTLIN_MIRROR") == "1") {
            maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2") {
                content { includeGroup("org.jetbrains.kotlin") }
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // CI-only, JetBrains-operated cache redirector for Kotlin artifacts.
        // Avoid Maven Central's GitHub-release redirect during runner HTTP 504.
        // Normal developer builds keep their original repository order.
        if (System.getenv("IR_CI_KOTLIN_MIRROR") == "1") {
            maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2") {
                content { includeGroup("org.jetbrains.kotlin") }
            }
        }
        mavenCentral()
    }
}
rootProject.name = "InvestmentRadar"
include(":app")
