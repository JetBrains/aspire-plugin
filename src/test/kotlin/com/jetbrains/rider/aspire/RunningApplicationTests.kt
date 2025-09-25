package com.jetbrains.rider.aspire

import com.intellij.execution.RunManager
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import org.testng.annotations.Test
import java.net.URL

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class RunningApplicationTests : PerTestSolutionTestBase() {
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

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running application from a generated run config launches dashboard`() {
        runTest(URL("http://localhost:15273"))
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running application from a generated run config launches web application`() {
        runTest(URL("http://localhost:5183"))
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running application from a generated run config launches api application`() {
        runTest(URL("http://localhost:5592/weatherforecast"))
    }

    private fun runTest(urlToCheck: URL) {
        val runManager = RunManager.getInstance(project)
        val configuration = runManager.allConfigurationsList
            .filterIsInstance<AspireHostConfiguration>()
            .singleOrNull { it.name == "DefaultAspireSolution.AppHost: http" }
        requireNotNull(configuration)
        runManager.selectedConfiguration = runManager.findSettings(configuration)
        buildSolutionWithReSharperBuild()
        runAspireProgram(project, urlToCheck)
    }
}