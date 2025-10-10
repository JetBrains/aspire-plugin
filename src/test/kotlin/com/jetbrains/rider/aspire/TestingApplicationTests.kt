package com.jetbrains.rider.aspire

import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.base.UnitTestingTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.scriptingApi.runAllUnitTestsFromProject
import com.jetbrains.rider.test.scriptingApi.withSolution
import org.testng.annotations.Test

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class TestingApplicationTests : UnitTestingTestBase() {
    @Test
    fun `Running xunit tests for aspire solution`() {
        withSolution("AspireSolutionWithXUnit", openSolutionParamsForBuild) {
            runAllUnitTestsFromProject("AspireSolutionWithXUnit.Tests", 4, 4, expectedSuccessful = 4)
        }
    }

    @Test
    fun `Running nunit tests for aspire solution`() {
        withSolution("AspireSolutionWithNUnit", openSolutionParamsForBuild) {
            runAllUnitTestsFromProject("AspireSolutionWithNUnit.Tests", 4, 4, expectedSuccessful = 4)
        }
    }
}