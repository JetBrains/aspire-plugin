package com.jetbrains.rider.aspire

import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.executeBeforeRunTasksForSelectedConfiguration
import org.testng.annotations.Test
import java.net.URI
import java.net.URL
import java.time.Duration

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

    override val testRunner: IntegrationTestRunner by lazy { IntegrationTestRunner(testProcessor, aspireLoggedErrorProcessor) }

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

    @Test
    @Solution("AspireSolutionWithExecutableLibrary")
    fun `Running aspire solution with executable library launches api application`() {
        //For a library project we have to build the whole solution to have binary files
        buildSolutionWithReSharperBuild()
        runTest("AppHost1: http", URI("http://localhost:5000").toURL())
    }

    private fun runTest(runConfigName: String, urlToCheck: URL) {
        selectAspireRunConfiguration(runConfigName, project)
        executeBeforeRunTasksForSelectedConfiguration(project, Duration.ofMinutes(1))
        runAspireProgram(project, urlToCheck)
    }
}