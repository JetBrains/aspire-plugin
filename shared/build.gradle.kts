plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intelliJPlatformModule)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.serializationJson)

    intellijPlatform {
        intellijIdea(providers.gradleProperty("ideaVersion")) {
            useCache = true
        }

        bundledModule("intellij.libraries.kotlinx.datetime")
        bundledModule("intellij.libraries.kotlinx.serialization.json")
    }
}

tasks.test {
    useJUnitPlatform()
}
