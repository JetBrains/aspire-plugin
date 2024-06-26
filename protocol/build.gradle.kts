import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    alias(libs.plugins.kotlin)
    id("com.jetbrains.rdgen") version libs.versions.rdGen
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
    val pluginSourcePath = projectDir.resolve("../src")
    val ktOutput = pluginSourcePath.resolve("main/kotlin/me/rafaelldi/aspire/generated")
    val csOutput = pluginSourcePath.resolve("dotnet/aspire-session-host/Generated")

    verbose = true
    packages = "model.sessionHost"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = ktOutput.canonicalPath
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = csOutput.canonicalPath
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
