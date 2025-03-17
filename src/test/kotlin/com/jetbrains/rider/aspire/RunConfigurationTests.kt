package com.jetbrains.rider.aspire

import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.executeWithGold
import org.testng.annotations.Test

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("DefaultAspireSolution")
class RunConfigurationTests : PerClassSolutionTestBase() {

    override val solutionApiFacade: SolutionApiFacade = object : RiderSolutionApiFacade() {
        override fun waitForSolution(params: OpenSolutionParams) {
            // This may sometimes take a long time on CI agents.
            params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)

            return super.waitForSolution(params)
        }
    }

    @Test
    fun `A run configuration should be generated for an opened solution`() {
        executeWithGold(testGoldFile) { printStream ->
            dumpAspireHostRunConfigurations(project, printStream)
        }
    }
}