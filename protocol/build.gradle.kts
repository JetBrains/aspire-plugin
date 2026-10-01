import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    alias(libs.plugins.kotlin)
    id("com.jetbrains.rdgen") version libs.versions.rdGen
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.rdGen)
    implementation(libs.kotlinStdLib)
    implementation(
        project(
            mapOf(
                "path" to ":",
                "configuration" to "riderModel"
            )
        )
    )
}

rdgen {
    val riderModulePath = projectDir.resolve("../rider/src/main/kotlin/com/jetbrains/aspire/rider")
    val pluginSourcePath = projectDir.resolve("../src")

    verbose = true
    packages = "model.aspirePlugin"

    val ktPluginOutput = riderModulePath.resolve("generated")
    val csPluginOutput = pluginSourcePath.resolve("dotnet/AspirePlugin/Generated")

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = ktPluginOutput.canonicalPath
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = csPluginOutput.canonicalPath
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
