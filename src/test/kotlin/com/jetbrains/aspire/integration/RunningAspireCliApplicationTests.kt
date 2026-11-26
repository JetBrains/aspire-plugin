package com.jetbrains.aspire.integration

import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Mute
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.junit5.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.io.path.div

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Tag(TeamCityTags.General.Season)
class RunningAspireCliApplicationTests : PerTestSolutionTestBase() {
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
    fun `Running default solution launches dashboard`() {
        createAndSelectDefaultAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:15273").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running default solution launches web app`() {
        createAndSelectDefaultAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:5183").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    @Test
    @Solution("DefaultAspireSolution")
    fun `Running default solution launches api app`() {
        createAndSelectDefaultAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:5592/weatherforecast").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun runningFileBasedSolutionLaunchesDashboard() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:15273").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun runningFileBasedSolutionLaunchesWebApp() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:5183").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    @Test
    @Solution("AspireFileBasedSolution")
    @Mute(FILE_BASED_APP_HOST_CLI_MUTE_REASON, platforms = [PlatformType.WINDOWS_ALL])
    fun runningFileBasedSolutionLaunchesApiApp() {
        buildSolutionWithReSharperBuild()
        createAndSelectFileBasedAspireSolutionConfiguration()

        runAspireProgram(project, URI("http://localhost:5592/weatherforecast").toURL(), ASPIRE_CLI_APP_HOST_STARTED_MESSAGE)
    }

    private fun createAndSelectDefaultAspireSolutionConfiguration() {
        val appHostPath = activeSolutionDirectory / "DefaultAspireSolution.AppHost" / "DefaultAspireSolution.AppHost.csproj"
        createAndSelectAspireCliConfiguration(project, appHostPath)
    }

    private fun createAndSelectFileBasedAspireSolutionConfiguration() {
        val appHostPath = activeSolutionDirectory / "apphost.cs"
        createAndSelectAspireCliConfiguration(project, appHostPath)
    }
}
