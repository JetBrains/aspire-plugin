package com.jetbrains.rider.aspire

import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.aspire.manifest.ManifestService
import com.jetbrains.rider.projectView.solutionDirectoryPath
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import com.jetbrains.rider.test.framework.executeWithGold
import org.testng.annotations.Test
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
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
        service.generateManifest(hostPath)

        val manifestFilePath = project.solutionDirectoryPath
            .resolve("DefaultAspireSolution.AppHost")
            .resolve("aspire-manifest.json")

        waitAndPump(Duration.ofMinutes(5), {
            manifestFilePath.exists()
        })

        manifestFilePath.exists().shouldBeTrue()

        executeWithGold(testGoldFile) { printStream ->
            printStream.print(manifestFilePath.readText())
        }
    }
}