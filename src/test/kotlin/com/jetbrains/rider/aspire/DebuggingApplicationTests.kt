package com.jetbrains.rider.aspire

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rdclient.util.idea.toVirtualFile
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.DebuggerTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.Test

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

    @Test
    @Solution("DefaultAspireSolution")
    fun `Debugging default aspire solution pauses at host project`() {
        val fileForBreakpoint = activeSolutionDirectory
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("AppHost.cs")
            .toVirtualFile(true)
        requireNotNull(fileForBreakpoint)
        runTest("DefaultAspireSolution.AppHost: http", fileForBreakpoint, 12)
    }

    private fun runTest(runConfigName: String, fileForBreakpoint: VirtualFile, lineForBreakpoint: Int) {
        selectAspireRunConfiguration(runConfigName, project)
        testDebugProgram(
            debugGoldFile,
            beforeRun = {
                toggleBreakpoint(fileForBreakpoint, lineForBreakpoint)
            }, test = {
                waitForPause()
                dumpFullCurrentData()
                resumeSession()
            },
            exitProcessAfterTest = true
        )
    }
}