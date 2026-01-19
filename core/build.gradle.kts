plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    id("org.jetbrains.intellij.platform.module")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

dependencies {
    implementation(libs.serializationJson)

    intellijPlatform {
        rider(providers.gradleProperty("platformVersion")) {
            useInstaller = false
            useCache = true
        }
        jetbrainsRuntime()
    }
}
