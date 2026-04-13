plugins {
    alias(libs.plugins.kotlin)
    id("org.jetbrains.intellij.platform.module")
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
