package com.jetbrains.aspire

import com.jetbrains.aspire.manifest.ManifestService
import com.jetbrains.rider.projectView.solutionDirectoryPath
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.runBlockingWithFlushing
import org.testng.annotations.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("DefaultAspireSolution")
class ManifestGenerationTests : PerClassSolutionTestBase() {
    override val solutionApiFacade: SolutionApiFacade = object : RiderSolutionApiFacade() {
        override fun waitForSolution(params: OpenSolutionParams) {
            // This may sometimes take a long time on CI agents.
            params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)

            return super.waitForSolution(params)
        }
    }

    @Test
    fun `A manifest service should generate an Aspire manifest for a host project`() {
        val service = ManifestService.getInstance(project)
        val hostPath = project.solutionDirectoryPath
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("DefaultAspireSolution.AppHost.csproj")

        runBlockingWithFlushing("Generating .NET Aspire manifest", 5.minutes) {
            service.generateManifest(hostPath)
        }

        val manifestFilePath = project.solutionDirectoryPath
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("aspire-manifest.json")

        manifestFilePath.exists().shouldBeTrue()

        executeWithGold(testGoldFile) { printStream ->
            printStream.print(manifestFilePath.readText())
        }
    }
}