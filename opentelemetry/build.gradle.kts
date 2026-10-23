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

        plugin("com.jetbrains.opentelemetry", libs.versions.openTelemetry.get())
    }

    implementation(project(":shared"))
    implementation(project(":core"))
}