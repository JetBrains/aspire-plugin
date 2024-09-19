pluginManagement {
    val rdVersion: String by settings
    val rdKotlinVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.jetbrains.rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${rdVersion}")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "aspire-plugin"

include(":protocol")
