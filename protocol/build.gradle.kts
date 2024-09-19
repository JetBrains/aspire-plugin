import com.jetbrains.rd.generator.gradle.RdGenTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.jetbrains.rdgen")
}

repositories {
    mavenCentral()
}

val aspireRepoRoot: File = projectDir.parentFile
val isMonorepo = rootProject.projectDir != aspireRepoRoot

dependencies {
    if (isMonorepo) {
        implementation(project(":rider-model"))
    } else {
        val rdVersion: String by project
        val rdKotlinVersion: String by project

        implementation("com.jetbrains.rd:rd-gen:$rdVersion")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$rdKotlinVersion")
        implementation(
            project(
                mapOf(
                    "path" to ":",
                    "configuration" to "riderModel"
                )
            )
        )
    }
}

data class EfCoreGeneratorSettings(
    val csSessionHostOutput: File,
    val csPluginOutput: File,
    val ktSessionHostOutput: File,
    val ktPluginOutput: File,
    val suffix: String)

val ktSessionHostOutputRelativePath = "main/kotlin/com/jetbrains/rider/aspire/generated"
val ktPluginOutputRelativePath = "main/kotlin/com/jetbrains/rider/aspire/generated"
val generatorSettings = if (isMonorepo) {
    val monorepoRoot =
        buildscript.sourceFile?.parentFile?.parentFile?.parentFile?.parentFile?.parentFile ?: error("Cannot find products home")
    check(monorepoRoot.resolve(".ultimate.root.marker").isFile) {
        error("Incorrect location in monorepo: monorepoRoot='$monorepoRoot'")
    }
    val monorepoPreGeneratedRootDir = monorepoRoot.resolve("dotnet/Plugins/_Aspire.Pregenerated")

    val monorepoPreGeneratedFrontendDir = monorepoPreGeneratedRootDir.resolve("Frontend")
    val backendModelRootDir = monorepoPreGeneratedRootDir.resolve("Backend")

    val monorepoPreGeneratedSessionHostBackendDir = backendModelRootDir.resolve("SessionHostModel")
    val monorepoPreGeneratedPluginBackendDir = backendModelRootDir.resolve("PluginModel")

    val ktSessionHostOutputMonorepoRoot = monorepoPreGeneratedFrontendDir.resolve(ktSessionHostOutputRelativePath)
    val ktPluginOutputMonorepoRoot = monorepoPreGeneratedFrontendDir.resolve(ktPluginOutputRelativePath)
    EfCoreGeneratorSettings(
        monorepoPreGeneratedSessionHostBackendDir,
        monorepoPreGeneratedPluginBackendDir,
        ktSessionHostOutputMonorepoRoot,
        ktPluginOutputMonorepoRoot,
        ".Pregenerated")
} else {
    val csSessionHostOutput = aspireRepoRoot.resolve("dotnet/aspire-session-host/Generated")
    val csPluginOutput = aspireRepoRoot.resolve("dotnet/aspire-plugin/Generated")
    val ktSessionHostOutput = aspireRepoRoot.resolve(ktSessionHostOutputRelativePath)
    val ktPluginOutput = aspireRepoRoot.resolve(ktPluginOutputRelativePath)
    EfCoreGeneratorSettings(
        csSessionHostOutput,
        csPluginOutput,
        ktSessionHostOutput,
        ktPluginOutput,
        ".Generated"
    )
}

rdgen {
    val pluginSourcePath = projectDir.resolve("../src")

    verbose = true
    packages = "model.sessionHost,model.aspirePlugin"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = generatorSettings.ktSessionHostOutput.canonicalPath
        generatedFileSuffix = generatorSettings.suffix
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "model.sessionHost.AspireSessionHostRoot"
        directory = generatorSettings.csSessionHostOutput.canonicalPath
        generatedFileSuffix = generatorSettings.suffix
    }

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = generatorSettings.ktPluginOutput.canonicalPath
        generatedFileSuffix = generatorSettings.suffix
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        directory = generatorSettings.csPluginOutput.canonicalPath
        generatedFileSuffix = generatorSettings.suffix
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
