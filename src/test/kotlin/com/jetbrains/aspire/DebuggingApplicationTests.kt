package com.jetbrains.aspire

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.DebuggerTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.scriptingApi.DebugTestExecutionContext
import com.jetbrains.rider.test.scriptingApi.executeBeforeRunTasksForSelectedConfiguration
import com.jetbrains.rider.test.scriptingApi.testDebugProgram
import com.jetbrains.rider.test.scriptingApi.toggleBreakpoint
import org.testng.annotations.Test
import java.time.Duration

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

    override val testRunner: IntegrationTestRunner by lazy {
        IntegrationTestRunner(
            testProcessor,
            aspireLoggedErrorProcessor
        )
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at host project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("AppHost.cs")
            .refreshAndFindVirtualFile()
        requireNotNull(fileForBreakpoint)
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            12,
            "DefaultAspireSolution.AppHost: http",
            1
        ) {
            dumpDebugContext()
        }
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at api project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.ApiService")
            .resolve("Program.cs")
            .refreshAndFindVirtualFile()
        requireNotNull(fileForBreakpoint)
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            43,
            "DefaultAspireSolution.ApiService",
            2
        ) {
            dumpDebugContext()
        }
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at web project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.Web")
            .resolve("Program.cs")
            .refreshAndFindVirtualFile()
        requireNotNull(fileForBreakpoint)
        runTest(
            "DefaultAspireSolution.AppHost: http",
            fileForBreakpoint,
            44,
            "DefaultAspireSolution.Web",
            3
        ) {
            dumpDebugContext()
        }
    }

    @Test
    @Solution("AspireSolutionWithExternalProject")
    fun `Debugging aspire solution with external project pauses at that project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("WebApplication1")
            .resolve("Program.cs")
            .refreshAndFindVirtualFile()
        requireNotNull(fileForBreakpoint)
        runTest(
            "AppHost1: http",
            fileForBreakpoint,
            6,
            "WebApplication1",
            2
        ) {
            dumpDebugContext()
        }
    }

    private fun runTest(
        runConfigName: String,
        fileForBreakpoint: VirtualFile,
        lineForBreakpoint: Int,
        debugSessionName: String,
        numOfSessionsExpectedToRun: Int,
        testDebugContext: DebugTestExecutionContext.() -> Unit
    ) {
        selectAspireRunConfiguration(runConfigName, project)
        executeBeforeRunTasksForSelectedConfiguration(project, Duration.ofMinutes(1))
        testDebugProgram(
            debugGoldFile,
            beforeRun = {
                toggleBreakpoint(fileForBreakpoint, lineForBreakpoint)
            }, test = {
                withSession(debugSessionName, numOfSessionsExpectedToRun) {
                    testDebugContext()
                }
            },
            exitProcessAfterTest = true
        )
    }
}