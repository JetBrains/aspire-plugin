plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intelliJPlatformModule)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.serializationJson)

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.rd.client.base")
        bundledModule("intellij.rider.rdclient.dotnet")
    }
}
