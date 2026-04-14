plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatformModule)
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("ideaVersion")) {
            useCache = true
        }

        compatiblePlugin("PythonCore")
    }

    implementation(project(":core"))
}
