package com.jetbrains.rider.aspire

import com.intellij.execution.RunManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.jetbrains.rd.util.string.printToString
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationType
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.test.maskCustomDotnetPath
import com.jetbrains.rider.test.scriptingApi.maskMachineSpecificPaths
import java.io.PrintStream

fun dumpAspireHostRunConfigurations(project: Project, printStream: PrintStream) {
    val runManagerEx = RunManagerEx.getInstanceEx(project)
    val allSettings = runManagerEx.allSettings.filter { it.type.id == AspireHostConfigurationType.ID }
    printStream.println("Aspire Host run configuration count: ${allSettings.size}")
    allSettings.map { it.configuration }.filterIsInstance<AspireHostConfiguration>().forEach {
        printStream.println("---")
        printStream.println("Name: ${it.name}")
        printStream.println("Type: ${it.type.displayName}")
        with(it.parameters) {
            printStream.println("Project filePath: ${maskMachineSpecificPaths(project, projectFilePath)}")
            printStream.println("Project TFM: $projectTfm")
            printStream.println("Launch settings profile name: $profileName")
            printStream.println("Arguments: ${maskMachineSpecificPaths(project, arguments)}")
            printStream.println("Working directory: ${maskMachineSpecificPaths(project, workingDirectory)}")
            dumpEnvironment(project, "Environment variables", envs, printStream)
            printStream.println("Use Podman: $usePodmanRuntime")
            dumpStartBrowserParameters(startBrowserParameters, printStream)
        }
    }
}

private fun dumpEnvironment(
    project: Project,
    title: String,
    env: Map<String, String>,
    printStream: PrintStream,
    maskDotnetPath: Boolean = false
) {
    var printableEnv = env

    if (maskDotnetPath) {
        printableEnv =
            printableEnv
                .map { (k, v) -> k to if (k == "PATH") v.maskCustomDotnetPath(true)!! else v }
                .toMap()
    }

    printableEnv = printableEnv.toSortedMap()

    printStream.println(
        "$title: ${
            printableEnv
                .mapValues { maskMachineSpecificPaths(project, maskSystemPath(it.value)) }
                .printToString()
                .replace("NUGET_PACKAGES=[^,}]+".toRegex(), "NUGET_PACKAGES={NUGET_FALLBACK_FOLDER}")
        }"
    )
}

private fun maskSystemPath(string: String): String {
    val systemPath = EnvironmentUtil.getValue("PATH") ?: return string
    return string.replace(systemPath, "{SYSTEM_PATH}")
}

private fun dumpStartBrowserParameters(startBrowserParameters: DotNetStartBrowserParameters, printStream: PrintStream) =
    with(startBrowserParameters) {
        printStream.println("Start browser on launch: $startAfterLaunch")
        printStream.println("Start browser url: $url")
        printStream.println("Start browser is start JavaScript debugger: $withJavaScriptDebugger")
        printStream.println("Start browser browser name: ${browser?.name}")
    }