package com.jetbrains.aspire

import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.junit5.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.scriptingApi.executeBeforeRunTasksForSelectedConfiguration
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL
import java.time.Duration

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Tag(TeamCityTags.General.Season)
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

    override val testLogManager by lazy { createAspireTestLogManager(traceScenarios, traceCategories) }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running default aspire solution launches dashboard`() {
        runTest("DefaultAspireSolution.AppHost: http", URI("http://localhost:15273").toURL())
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running default aspire solution launches web application`() {
        runTest("DefaultAspireSolution.AppHost: http", URI("http://localhost:5183").toURL())
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running default aspire solution launches api application`() {
        runTest("DefaultAspireSolution.AppHost: http", URI("http://localhost:5592/weatherforecast").toURL())
    }

    @Test
    @Solution("AspireSolutionWithExternalProject")
    fun `Running aspire solution with external project launches api application`() {
        runTest("AppHost1: http", URI("http://localhost:5123").toURL())
    }

    private fun runTest(runConfigName: String, urlToCheck: URL) {
        selectAspireRunConfiguration(runConfigName, project)
        executeBeforeRunTasksForSelectedConfiguration(project, Duration.ofMinutes(1))
        runAspireProgram(project, urlToCheck)
    }
}
