package com.jetbrains.aspire

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rdclient.util.idea.toVirtualFile
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.DebuggerTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.Test
import java.time.Duration

@Mute
@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class DebuggingApplicationTests : DebuggerTestBase() {
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

    override val testRunner: IntegrationTestRunner by lazy { IntegrationTestRunner(testProcessor,
        aspireLoggedErrorProcessor
    ) }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at host project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("AppHost.cs")
            .toVirtualFile(true)
        requireNotNull(fileForBreakpoint)
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            12
        ) {
            trackAllDebugSessions(3) {
                dumpDebugContext()
            }
        }
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at api project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.ApiService")
            .resolve("Program.cs")
            .toVirtualFile(true)
        requireNotNull(fileForBreakpoint)
        val apiProjectPath = activeSolutionDirectory
            .resolve("DefaultAspireSolution.ApiService")
            .resolve("DefaultAspireSolution.ApiService.csproj")
            .toPath()
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            43
        ) {
            trackAllDebugSessions(3) {
                dumpDebugContextForProject(apiProjectPath)
            }
        }
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at web project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.Web")
            .resolve("Program.cs")
            .toVirtualFile(true)
        requireNotNull(fileForBreakpoint)
        val webProjectPath = activeSolutionDirectory
            .resolve("DefaultAspireSolution.Web")
            .resolve("DefaultAspireSolution.Web.csproj")
            .toPath()
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            44
        ) {
            trackAllDebugSessions(3) {
                dumpDebugContextForProject(webProjectPath)
            }
        }
    }

    @Test
    @Solution("AspireSolutionWithExternalProject")
    fun `Debugging aspire solution with external project pauses at that project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("WebApplication1")
            .resolve("Program.cs")
            .toVirtualFile(true)
        requireNotNull(fileForBreakpoint)
        val externalProjectPath = activeSolutionDirectory
            .resolve("WebApplication1")
            .resolve("WebApplication1.csproj")
            .toPath()
        runTest(
            "AppHost1: http",
            fileForBreakpoint,
            6
        ) {
            trackAllDebugSessions(2) {
                dumpDebugContextForProject(externalProjectPath)
            }
        }
    }

    private fun runTest(
        runConfigName: String,
        fileForBreakpoint: VirtualFile,
        lineForBreakpoint: Int,
        testDebugContext: DebugTestExecutionContext.() -> Unit
    ) {
        selectAspireRunConfiguration(runConfigName, project)
        executeBeforeRunTasksForSelectedConfiguration(project, Duration.ofMinutes(1))
        testDebugProgram(
            debugGoldFile,
            beforeRun = {
                toggleBreakpoint(fileForBreakpoint, lineForBreakpoint)
            }, test = {
                testDebugContext()
            },
            exitProcessAfterTest = true
        )
    }

    fun DebugTestExecutionContext.trackAllDebugSessions(
        sessionsNum: Int,
        action: DebugTestExecutionContext.() -> Unit
    ) {
        action()

        val timeout = Duration.ofSeconds(30)
        if (!pumpMessages(timeout) { XDebuggerManager.getInstance(project).debugSessions.size == sessionsNum })
            logger.error("Couldn't get $sessionsNum debug sessions running within $timeout seconds")

        for (debugSession in XDebuggerManager.getInstance(project).debugSessions) {
            if (debugSession != session)
                shutdownDebuggerSession(debugSession, true)
        }
    }
}