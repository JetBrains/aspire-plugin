plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
}

dependencies {
    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
    }

    implementation(project(":core"))
}
