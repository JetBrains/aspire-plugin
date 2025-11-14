import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    alias(libs.plugins.kotlin)
    id("com.jetbrains.rdgen") version libs.versions.rdGen
}

repositories {
    mavenCentral()
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
    val coreModulePath = projectDir.resolve("../core/src/main/kotlin/com/jetbrains/rider/aspire")
    val pluginSourcePath = projectDir.resolve("../src")

    verbose = true
    packages = "model.aspireWorker,model.aspirePlugin"

    val ktWorkerOutput = coreModulePath.resolve("generated")
    val csWorkerOutput = pluginSourcePath.resolve("dotnet/AspireWorker/Generated")

    generator {
        language = "kotlin"
        transform = "asis"
        root = "model.aspireWorker.AspireWorkerRoot"
        directory = ktWorkerOutput.canonicalPath
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "model.aspireWorker.AspireWorkerRoot"
        directory = csWorkerOutput.canonicalPath
    }

    val ktPluginOutput = coreModulePath.resolve("generated")
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
