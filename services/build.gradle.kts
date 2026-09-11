plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("ideaVersion")) {
            useCache = true
        }

        bundledPlugins("intellij.execution.serviceView.plugin")
    }

    implementation(project(":shared"))
    implementation(project(":core"))
}
