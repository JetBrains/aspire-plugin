package com.jetbrains.rider.aspire

import com.intellij.execution.RunManager
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import org.testng.annotations.Test

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("DefaultAspireSolution")
class LaunchingApplicationTests : PerClassSolutionTestBase() {
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
    fun `Launch application from a generated run config`() {
        val runManager = RunManager.getInstance(project)
        val configuration = runManager.allConfigurationsList
            .filterIsInstance<AspireHostConfiguration>()
            .singleOrNull { it.name == "DefaultAspireSolution.AppHost: http" }
        requireNotNull(configuration)
        configuration.parameters.apply {
            startBrowserParameters.apply {
                startAfterLaunch = false
            }
        }
        runManager.selectedConfiguration = runManager.findSettings(configuration)
    }
}