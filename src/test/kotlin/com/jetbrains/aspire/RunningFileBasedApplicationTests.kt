package com.jetbrains.aspire

import com.intellij.execution.RunManager
import com.jetbrains.aspire.rider.run.AspireConfigurationType
import com.jetbrains.aspire.rider.run.file.AspireFileConfiguration
import com.jetbrains.aspire.rider.run.file.AspireFileConfigurationFactory
import com.jetbrains.aspire.rider.run.file.AspireFileConfigurationParameters
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithReSharperBuild
import com.jetbrains.rider.test.scriptingApi.div
import com.jetbrains.rider.test.scriptingApi.executeBeforeRunTasksForSelectedConfiguration
import org.testng.annotations.Test
import java.net.URI
import java.net.URL
import java.time.Duration

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class RunningFileBasedApplicationTests : PerTestSolutionTestBase() {
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

    override val testRunner: IntegrationTestRunner by lazy { IntegrationTestRunner(testProcessor,
        aspireLoggedErrorProcessor
    ) }

    @Test
    @Solution("AspireFileBasedDefaultAspireSolution")
    fun `Running aspire file based default solution launches dashboard`() {
        buildSolutionWithReSharperBuild()
        createAndSelectAspireFileConfiguration()
        runTestForSelectedConfiguration(URI("http://localhost:15273").toURL())
    }

    @Test
    @Solution("AspireFileBasedDefaultAspireSolution")
    fun `Running aspire file based default solution launches web application`() {
        buildSolutionWithReSharperBuild()
        createAndSelectAspireFileConfiguration()
        runTestForSelectedConfiguration(URI("http://localhost:5183").toURL())
    }

    @Test
    @Solution("AspireFileBasedDefaultAspireSolution")
    fun `Running aspire file based default solution launches api application`() {
        buildSolutionWithReSharperBuild()
        createAndSelectAspireFileConfiguration()
        runTestForSelectedConfiguration(URI("http://localhost:5592/weatherforecast").toURL())
    }

    @Test
    @Solution("AspireFileBasedSolutionWithExecutableLibrary")
    fun `Running aspire file based solution with executable library launches api application`() {
        buildSolutionWithReSharperBuild()
        createAndSelectAspireFileConfiguration()
        runTestForSelectedConfiguration(URI("http://localhost:5000").toURL())
    }

    private fun createAndSelectAspireFileConfiguration() {
        val filePath = (activeSolutionDirectory / "apphost.cs").toString()
        val factory = AspireFileConfigurationFactory(AspireConfigurationType())
        val configuration = AspireFileConfiguration(project, factory, "AppHost-File-Test", AspireFileConfigurationParameters(
            project,
            filePath = filePath,
            profileName = "http",
            trackArguments = true,
            arguments = "",
            trackWorkingDirectory = true,
            workingDirectory = activeSolutionDirectory.toString(),
            trackEnvs = true,
            envs = hashMapOf(),
            trackUrl = true,
            trackBrowserLaunch = true,
            usePodmanRuntime = false,
            startBrowserParameters = DotNetStartBrowserParameters()
        ))
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration(configuration, factory)
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
    }

    private fun runTestForSelectedConfiguration(urlToCheck: URL) {
        executeBeforeRunTasksForSelectedConfiguration(project, Duration.ofMinutes(1))
        runAspireProgram(project, urlToCheck)
    }
}