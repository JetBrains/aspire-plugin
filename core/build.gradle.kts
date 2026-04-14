plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intelliJPlatformModule)
}

dependencies {
    compileOnly(libs.serializationJson)

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
    }
}
