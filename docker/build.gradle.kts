plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("ideaVersion")) {
            useCache = true
        }

        bundledPlugins("Docker")
    }

    implementation(project(":core"))
}
