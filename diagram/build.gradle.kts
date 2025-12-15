plugins {
    alias(libs.plugins.kotlin)
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
        nightly()
    }
}

dependencies {
    intellijPlatform {
        rider(providers.gradleProperty("platformVersion")) {
            useInstaller = false
            useCache = true
        }
        jetbrainsRuntime()

        pluginModule(implementation(project(":core")))

        bundledPlugins("com.intellij.diagram")
    }
}
