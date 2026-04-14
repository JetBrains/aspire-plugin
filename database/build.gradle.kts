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

        bundledPlugins("com.intellij.database", "Docker", "rider.intellij.plugin.appender")
    }

    implementation(project(":core"))
}
