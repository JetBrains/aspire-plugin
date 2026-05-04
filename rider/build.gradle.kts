plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
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
        bundledModules("intellij.rider.rdclient.dotnet")
    }

    implementation(project(":core"))
}
