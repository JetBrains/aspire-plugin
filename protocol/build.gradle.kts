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
    val pluginSourcePath = projectDir.resolve("../src")

    verbose = true
    packages = "model.sessionHost,model.aspirePlugin"

    val ktSessionHostOutput = pluginSourcePath.resolve("main/kotlin/me/rafaelldi/aspire/generated")
    val csSessionHostOutput = pluginSourcePath.resolve("dotnet/aspire-session-host/Generated")

    generator {
        language = "kotlin"
        transform = "asis"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = ktSessionHostOutput.canonicalPath
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = csSessionHostOutput.canonicalPath
    }

    val ktPluginOutput = pluginSourcePath.resolve("main/kotlin/me/rafaelldi/aspire/generated")
    val csPluginOutput = pluginSourcePath.resolve("dotnet/aspire-plugin/Generated")

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
