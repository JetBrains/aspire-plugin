package com.jetbrains.aspire.integration

import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.debugger.XDebuggerTestHelper
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.flushQueues
import com.jetbrains.rider.test.junit5.base.debugger.DebuggerTestBase
import com.jetbrains.rider.test.scriptingApi.DebugTestExecutionContext
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.toggleBreakpoint
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.div

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Tag(TeamCityTags.General.Season)
class DebuggingAspireCliApplicationTests : DebuggerTestBase() {
    override val solutionApiFacade: SolutionApiFacade = object : RiderSolutionApiFacade() {
        override fun waitForSolution(params: OpenSolutionParams) {
            // This may sometimes take a long time on CI agents.
            params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)
            return super.waitForSolution(params)
        }
    }

    override fun modifyOpenSolutionParams(params: OpenSolutionParams) {
        super.modifyOpenSolutionParams(params)
        params.restoreNuGetPackages = true
        params.waitForCaches = true
    }

    override val projectName = "DefaultAspireSolution"

    override val testLogManager by lazy { createAspireTestLogManager(traceScenarios, traceCategories) }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default solution pauses at host project`() {
        createAndSelectDefaultAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "DefaultAspireSolution.AppHost" / "AppHost.cs")

        runTest(fileForBreakpoint, 14, attachedSessionName("DefaultAspireSolution.AppHost"))
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default solution pauses at api project`() {
        createAndSelectDefaultAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "DefaultAspireSolution.ApiService" / "Program.cs")

        runTest(fileForBreakpoint, 43, "DefaultAspireSolution.ApiService")
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default solution pauses at web project`() {
        createAndSelectDefaultAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "DefaultAspireSolution.Web" / "Program.cs")

        runTest(fileForBreakpoint, 44, "DefaultAspireSolution.Web")
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun debuggingFileBasedSolutionPausesAtHostFile() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "apphost.cs")

        runTest(fileForBreakpoint, 23, "apphost")
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun debuggingFileBasedSolutionPausesAtApiProject() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "AspireFileBasedSolution.ApiService" / "Program.cs")

        runTest(fileForBreakpoint, 43, "AspireFileBasedSolution.ApiService")
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun debuggingFileBasedSolutionPausesAtWebProject() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()
        val fileForBreakpoint = findFile(activeSolutionDirectory / "AspireFileBasedSolution.Web" / "Program.cs")

        runTest(fileForBreakpoint, 44, "AspireFileBasedSolution.Web")
    }

    private fun createAndSelectDefaultAspireSolutionConfiguration() {
        val appHostPath = activeSolutionDirectory / "DefaultAspireSolution.AppHost" / "DefaultAspireSolution.AppHost.csproj"
        createAndSelectAspireCliConfiguration(project, appHostPath)
    }

    private fun createAndSelectFileBasedAspireSolutionConfiguration() {
        val appHostPath = activeSolutionDirectory / "apphost.cs"
        createAndSelectAspireCliConfiguration(project, appHostPath)
    }

    private fun findFile(path: Path): VirtualFile =
        requireNotNull(path.refreshAndFindVirtualFile()) { "File $path is not found" }

    // The Aspire CLI run configuration starts the CLI process without a debug session and attaches
    // the debugger to the AppHost later, so the debug session is looked up by name instead of being taken
    // from the started run content.
    private fun runTest(fileForBreakpoint: VirtualFile, lineForBreakpoint: Int, debugSessionName: String) {
        setBreakpointInOpenedFile(fileForBreakpoint, lineForBreakpoint)
        val cliProcessHandler = startSelectedConfigurationWithDebugger()

        try {
            executeWithGold(debugGoldFile) { stream ->
                val session = waitForDebugSession(debugSessionName)
                DebugTestExecutionContext(stream, session).dumpDebugContext()
            }
        } finally {
            stopDebugSessionsAndProcess(cliProcessHandler)
        }
    }

    // A file-based AppHost isn't part of any project until the run starts, and the backend offers breakpoint
    // positions in such a file only while it's opened in an editor, as it is when a user sets a breakpoint.
    private fun setBreakpointInOpenedFile(file: VirtualFile, line: Int) {
        FileEditorManager.getInstance(project).openFile(file, true)
        flushQueues()

        toggleBreakpoint(project, file, line)

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        waitAndPump(Duration.ofSeconds(30), {
            breakpointManager.allBreakpoints.any { it is XLineBreakpoint<*> && it.fileUrl == file.url }
        }) { "The breakpoint wasn't set in ${file.name}" }
    }

    @Suppress("JetBrainsInternalApiUsage")
    private fun startSelectedConfigurationWithDebugger(): ProcessHandler {
        val settings = RunManager.getInstance(project).selectedConfiguration
            ?: throw AssertionError("No configuration selected")
        val executor = DefaultDebugExecutor.getDebugExecutorInstance()
        val runner = ProgramRunner.getRunner(executor.id, settings.configuration)
            ?: throw AssertionError("No runner for ${settings.name} with the debug executor")
        val environment = ExecutionEnvironment(executor, runner, settings, project)
        var processHandler: ProcessHandler? = null
        environment.callback = ProgramRunner.Callback { processHandler = it?.processHandler }

        runner.execute(environment)

        waitAndPump(Duration.ofMinutes(1), { processHandler != null }) { "Aspire CLI process wasn't started" }
        return checkNotNull(processHandler)
    }

    // Rider names a session attached to a process `<pid>:<executable name without extension>`. Outside Windows,
    // the AppHost executable has no `.exe` extension, so the part after the last dot is dropped from its name instead.
    private fun attachedSessionName(executableName: String): String =
        if (SystemInfo.isWindows) executableName else executableName.substringBeforeLast('.')

    private fun waitForDebugSession(name: String): XDebugSession {
        val debuggerManager = XDebuggerManager.getInstance(project)
        fun findSession() = debuggerManager.debugSessions.firstOrNull {
            it.sessionName == name || it.sessionName.substringAfter(':') == name
        }

        waitAndPump(Duration.ofMinutes(5), { findSession() != null }) {
            "Unable to find \"$name\" debug session. Running sessions: ${debuggerManager.debugSessions.map { it.sessionName }}"
        }
        return checkNotNull(findSession())
    }

    private fun stopDebugSessionsAndProcess(cliProcessHandler: ProcessHandler) {
        val sessions = XDebuggerManager.getInstance(project).debugSessions.toList()
        sessions.forEach { it.stop() }
        waitAndPump(Duration.ofMinutes(1), { sessions.all { it.isStopped } }) { "Debug sessions didn't stop in time" }
        sessions.forEach { XDebuggerTestHelper.disposeDebugSession(it) }

        cliProcessHandler.destroyProcess()
        waitAndPump(Duration.ofMinutes(1), { cliProcessHandler.isProcessTerminated }) {
            "Aspire CLI process didn't stop in time"
        }
    }
}
