package com.jetbrains.aspire

import com.intellij.execution.RunManagerEx
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.aspire.rider.run.host.AspireHostConfiguration
import com.jetbrains.rd.platform.util.TimeoutTracker
import com.jetbrains.rd.util.reactive.hasValue
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rd.util.string.printToString
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.test.framework.flushQueues
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.maskCustomDotnetPath
import com.jetbrains.rider.test.scriptingApi.*
import java.io.PrintStream
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertNotNull

fun dumpAspireHostRunConfigurations(project: Project, printStream: PrintStream) {
    val runManagerEx = RunManagerEx.getInstanceEx(project)
    val allSettings = runManagerEx.allSettings.filter { it.type.id == "AspireHostConfiguration" }
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

fun selectAspireRunConfiguration(runConfigName: String, project: Project) {
    selectFirstRunConfiguration(project) {
        it is AspireHostConfiguration && it.name == runConfigName
    }
}

fun runAspireProgram(project: Project, url: URL) {
    val settings = RunManagerEx.getInstanceEx(project).selectedConfiguration
        ?: throw AssertionError("No configuration selected")

    var isApplicationReady = false
    val output = StringBuilder()
    val processHandler = startRunConfigurationProcess(
        project,
        settings,
        Duration.ofSeconds(30),
        ProcessOutputLogger(
            {
                output.append(it)
                if (it.contains("Distributed application started")) isApplicationReady = true
            },
            ProcessOutputType::isStdout,
            allowOutputBuffering = false
        )
    )

    val timeoutTracker = TimeoutTracker(Duration.ofSeconds(30))
    while (!isApplicationReady) {
        flushQueues()
        timeoutTracker.throwIfExpired { "Application didn't start in time" }
    }

    try {
        val trigger = connectToUrlOnBackgroundThread(url)
        waitAndPump(Duration.ofSeconds(30), { trigger.hasValue })
        frameworkLogger.trace(trigger.valueOrThrow.unwrap())
    } finally {
        processHandler.stop()
    }
}

fun DebugTestExecutionContext.dumpDebugContext() {
    waitForPause()
    dumpFullCurrentData()
    resumeSession()
}

fun DebugTestExecutionContext.dumpDebugContextForProject(projectPath: Path) {
    val session = findDebugSessionForProject(projectPath, project)
    val context = DebugTestExecutionContext(this.stream, session)
    context.dumpDebugContext()
}

private fun findDebugSessionForProject(projectPath: Path, project: Project): XDebugSession {
    val debuggerManager = XDebuggerManager.getInstance(project)
    var targetSession: XDebugSession? = null
    waitAndPump(Duration.ofMinutes(3), {
        val sessions = debuggerManager.debugSessions
        for (session in sessions) {
            val sessionRunProfile = session.runProfile
            if (sessionRunProfile !is DotNetSessionProfile) continue
            if (sessionRunProfile.projectPath == projectPath) {
                targetSession = session
                return@waitAndPump true
            }
        }
        return@waitAndPump false
    })
    assertNotNull(targetSession)
    return targetSession
}