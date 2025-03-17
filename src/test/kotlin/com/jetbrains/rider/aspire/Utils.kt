package com.jetbrains.rider.aspire

import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.jetbrains.rd.util.string.printToString
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.test.maskCustomDotnetPath
import com.jetbrains.rider.test.scriptingApi.maskMachineSpecificPaths
import java.io.PrintStream

fun dumpEnvironment(
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

fun dumpStartBrowserParameters(startBrowserParameters: DotNetStartBrowserParameters, printStream: PrintStream) =
    with(startBrowserParameters) {
        printStream.println("Start browser on launch: $startAfterLaunch")
        printStream.println("Start browser url: $url")
        printStream.println("Start browser is start JavaScript debugger: $withJavaScriptDebugger")
        printStream.println("Start browser browser name: ${browser?.name}")
    }