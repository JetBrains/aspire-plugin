package com.jetbrains.aspire

import com.jetbrains.rd.platform.diagnostics.LogTraceScenario
import com.jetbrains.rider.diagnostics.LogTraceScenarios
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.junit5.base.PerTestSettingsTestBase
import com.jetbrains.rider.test.scriptingApi.runAllUnitTestsFromProject
import com.jetbrains.rider.test.scriptingApi.withSolution
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Tag(TeamCityTags.General.Season)
class TestingApplicationTests : PerTestSettingsTestBase() {

    override val traceScenarios: Set<LogTraceScenario>
        get() = setOf(LogTraceScenarios.UnitTestingChannel, LogTraceScenarios.UnitTestingBackend)

    private val openSolutionParamsForBuild: OpenSolutionParams
        get() = OpenSolutionParams().apply {
            restoreNuGetPackages = true
            waitForCaches = true
            waitForSolutionBuilder = true
        }

    override val testLogManager by lazy { createAspireTestLogManager(traceScenarios, traceCategories) }

    @Test
    fun `Running xunit tests for aspire solution`() {
        withSolution("AspireSolutionWithXUnit", openSolutionParamsForBuild) {
            runAllUnitTestsFromProject(
                "AspireSolutionWithXUnit.Tests",
                4,
                4,
                expectedSuccessful = 4,
                timeout = Duration.ofMinutes(3)
            )
        }
    }

    @Test
    fun `Running nunit tests for aspire solution`() {
        withSolution("AspireSolutionWithNUnit", openSolutionParamsForBuild) {
            runAllUnitTestsFromProject(
                "AspireSolutionWithNUnit.Tests",
                4,
                4,
                expectedSuccessful = 4,
                timeout = Duration.ofMinutes(3)
            )
        }
    }
}
