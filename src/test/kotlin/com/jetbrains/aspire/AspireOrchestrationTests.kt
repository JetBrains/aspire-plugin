package com.jetbrains.aspire

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.rider.orchestration.AspireOrchestrationService
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.persistAllFilesOnDisk
import com.jetbrains.rider.test.framework.runner.IntegrationTestRunner
import com.jetbrains.rider.test.scriptingApi.*
import org.testng.annotations.Test
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class AspireOrchestrationTests : PerTestSolutionTestBase() {
    override fun modifyOpenSolutionParams(params: OpenSolutionParams) {
        super.modifyOpenSolutionParams(params)
        params.restoreNuGetPackages = true
        params.waitForCaches = true
        params.waitForSolutionBuilder = true
        params.restoreNuGetPackages = true
    }

    override val testRunner: IntegrationTestRunner by lazy {
        IntegrationTestRunner(
            testProcessor,
            aspireLoggedErrorProcessor
        )
    }

    @Test
    @Solution("DefaultAspNetCoreSolution")
    fun `Add aspire orchestration for asp net core project`() {
        runTest()
    }

    @Test
    @Solution("SolutionWithMultipleAspNetCoreProjects")
    fun `Add aspire orchestration for multiple asp net core projects`() {
        val projectNames = listOf("Project1", "Project2", "Project3")
        runTest(projectNames = projectNames)
    }

    @Test
    @Solution("DefaultWorkerSolution")
    fun `Add aspire orchestration for worker project`() {
        runTest()
    }

    @Test
    @Solution("DefaultMauiSolution")
    fun `Add aspire orchestration for maui project`() {
        runTest(serviceDefaultsName = "DefaultMauiSolution.Maui.ServiceDefaults", projectMainFileName = "MauiProgram.cs")
    }

    private fun runTest(
        projectNames: List<String> = listOf(activeSolution),
        appHostName: String = "$activeSolution.AppHost",
        serviceDefaultsName: String = "$activeSolution.ServiceDefaults",
        projectMainFileName: String = "Program.cs"
    ) {
        prepareProjectView(project)

        val solutionDirectoryPath = activeSolutionDirectory.toPath()
        val solution = activeSolutionDirectory.toPath().resolve("$activeSolution.sln")
        val hostProjectPath =
            solutionDirectoryPath.resolve(appHostName).resolve("$appHostName.csproj")
        val sharedProjectPath =
            solutionDirectoryPath.resolve(serviceDefaultsName).resolve("$serviceDefaultsName.csproj")
        val projectPaths = projectNames
            .map { solutionDirectoryPath.resolve(it).resolve("$it.csproj") }

        val service = AspireOrchestrationService.getInstance(project)
        runBlockingWithFlushing("Adding .NET Aspire Orchestration", 5.minutes) {
            val projectEntities = project.serviceAsync<WorkspaceModel>()
                .findProjects()
                .filter { it.url?.toPath()?.let { path -> projectPaths.contains(path) } ?: false }
            projectEntities.size.shouldBe(projectNames.size)

            service.addAspireOrchestration(projectEntities)
        }

        waitAllCommandsFinished()
        refreshFileSystem(project)
        persistAllFilesOnDisk()

        hostProjectPath.exists().shouldBeTrue()
        sharedProjectPath.exists().shouldBeTrue()

        executeWithGold(testGoldFile) { printStream ->
            printStream.printAspireFiles(solution, hostProjectPath, sharedProjectPath)
            for (projectPath in projectPaths) {
                printStream.printProjectFiles(projectPath, projectMainFileName)
            }
        }
    }

    private fun PrintStream.printAspireFiles(solution: Path, hostProjectPath: Path, sharedProjectPath: Path) {
        println("Solution file:")
        println()
        println(solution.readText())
        println()
        println("AppHost:")
        println()
        println(hostProjectPath.readText())
        println()
        println("AppHost.AppHost.cs:")
        println()
        println(hostProjectPath.parent.resolve("AppHost.cs").readText())
        println()
        println("ServiceDefaults:")
        println()
        println(sharedProjectPath.readText())
        println()
    }

    private fun PrintStream.printProjectFiles(projectPath: Path, projectMainFileName: String) {
        println("Project(${projectPath.name}):")
        println()
        println(projectPath.readText())
        println()
        println("Project(${projectPath.name}) $projectMainFileName:")
        println()
        println(projectPath.parent.resolve(projectMainFileName).readText())
        println()
    }
}