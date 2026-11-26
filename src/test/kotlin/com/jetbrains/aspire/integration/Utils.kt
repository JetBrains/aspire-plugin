package com.jetbrains.aspire.integration

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.jetbrains.aspire.rider.run.host.AspireHostConfiguration
import com.jetbrains.aspire.run.cli.AspireCliConfigurationType
import com.jetbrains.rd.platform.util.TimeoutTracker
import com.jetbrains.rd.util.reactive.hasValue
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rd.util.string.printToString
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.test.framework.flushQueues
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.maskCustomDotnetPath
import com.jetbrains.rider.test.scriptingApi.*
import org.jdom.Element
import java.io.PrintStream
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

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

// The AppHost prints this line after it has started
const val APP_HOST_STARTED_MESSAGE = "Distributed application started"

// The Aspire CLI prints this line after the AppHost and its dashboard have started
const val ASPIRE_CLI_APP_HOST_STARTED_MESSAGE = "to stop the AppHost and exit"

const val FILE_BASED_APP_HOST_CLI_MUTE_REASON =
    "The coordinated build of a file-based AppHost runs `dotnet build -mt` on a project generated in " +
        "%TEMP%\\dotnet\\runfile\\apphost-<hash>\\obj\\.aspire\\build with relative project paths. On Windows without " +
        "long path support, MSBuild in the multithreaded mode can't find a project if the unnormalized path exceeds " +
        "MAX_PATH (MSB3202), which is always the case for test solutions in %TEMP%\\JetTestRider"

// The CLI run configuration options are internal to the core module,
// so they are filled in the same way as when the configuration is loaded from the workspace file.
fun createAndSelectAspireCliConfiguration(project: Project, appHostPath: Path) {
    val runManager = RunManager.getInstance(project)
    val configurationType = runConfigurationType<AspireCliConfigurationType>()
    val settings = runManager.createConfiguration("AppHost-Cli-Test", configurationType)
    val optionsElement = createAspireCliOptionsElement(appHostPath, ASPIRE_CLI_HTTP_ENVIRONMENT_VARIABLES)
    settings.configuration.readExternal(optionsElement)
    runManager.addConfiguration(settings)
    runManager.selectedConfiguration = settings
}

// The Aspire CLI starts the AppHost with its first launch profile, which is usually `https`.
// Test agents don't have the ASP.NET Core development certificate, so the `http` profile is selected instead,
// and unsecured transport is allowed for it because the CLI doesn't do this by itself.
private val ASPIRE_CLI_HTTP_ENVIRONMENT_VARIABLES = mapOf(
    "DOTNET_LAUNCH_PROFILE" to "http",
    "ASPIRE_ALLOW_UNSECURED_TRANSPORT" to "true"
)

private fun createAspireCliOptionsElement(appHostPath: Path, environmentVariables: Map<String, String>): Element {
    val appHostFilePathOption = Element("option")
        .setAttribute("name", "appHostFilePath")
        .setAttribute("value", appHostPath.toString())
    val environmentVariablesElement = Element("envs")
    for ((name, value) in environmentVariables) {
        val environmentVariableElement = Element("env")
            .setAttribute("name", name)
            .setAttribute("value", value)
        environmentVariablesElement.addContent(environmentVariableElement)
    }

    return Element("configuration")
        .addContent(appHostFilePathOption)
        .addContent(environmentVariablesElement)
}

fun runAspireProgram(project: Project, url: URL, readyMessage: String = APP_HOST_STARTED_MESSAGE) {
    val settings = RunManagerEx.getInstanceEx(project).selectedConfiguration
        ?: throw AssertionError("No configuration selected")

    val isApplicationReady = AtomicBoolean()
    val output = StringBuffer()
    val processHandler = startRunConfigurationProcess(
        project,
        settings,
        Duration.ofSeconds(30),
        ProcessOutputLogger(
            {
                output.append(it)
                if (output.contains(readyMessage)) isApplicationReady.set(true)
            },
            { ProcessOutputType.isStdout(it) || ProcessOutputType.isStderr(it) },
            allowOutputBuffering = false
        )
    )

    try {
        val timeoutTracker = TimeoutTracker(Duration.ofMinutes(5))
        while (!isApplicationReady.get()) {
            flushQueues()
            check(!processHandler.isProcessTerminated) {
                "Application exited with code ${processHandler.exitCode} before startup. Process output:\n$output"
            }
            timeoutTracker.throwIfExpired { "Application didn't start in time. Process output:\n$output" }
        }

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
