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

        bundledModule("intellij.platform.backend")
        bundledModule("intellij.platform.rpc.backend")
    }

    implementation(project(":shared"))
    implementation(project(":core"))
}
