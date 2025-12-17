package com.jetbrains.aspire

import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.UnitTestingTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.scriptingApi.runAllUnitTestsFromProject
import com.jetbrains.rider.test.scriptingApi.withSolution
import org.testng.annotations.Test
import java.time.Duration

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class TestingApplicationTests : UnitTestingTestBase() {

    override val testRunner: IntegrationTestRunner by lazy {
        IntegrationTestRunner(
            testProcessor,
            aspireLoggedErrorProcessor
        )
    }

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