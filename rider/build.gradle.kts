plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
    alias(libs.plugins.serialization)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.rd.client.base")
        bundledModule("intellij.rd.client")
        bundledModule("intellij.rider.rdclient.dotnet")
        bundledModule("intellij.rider.languages")
        bundledModule("intellij.platform.debugger")
        bundledModule("intellij.platform.debugger.impl")
    }

    implementation(project(":core"))
}
