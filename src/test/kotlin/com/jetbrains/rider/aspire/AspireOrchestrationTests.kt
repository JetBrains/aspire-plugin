package com.jetbrains.rider.aspire

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.aspire.orchestration.AspireOrchestrationService
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.framework.persistAllFilesOnDisk
import com.jetbrains.rider.test.scriptingApi.prepareProjectView
import com.jetbrains.rider.test.scriptingApi.refreshFileSystem
import com.jetbrains.rider.test.scriptingApi.runBlockingWithFlushing
import com.jetbrains.rider.test.scriptingApi.waitAllCommandsFinished
import org.testng.annotations.Test
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class AspireOrchestrationTests : PerTestSolutionTestBase() {
    @Test
    @Solution("DefaultAspNetCoreSolution")
    fun `Add aspire orchestration for asp net core project`() {
        runTest(listOf(activeSolution), "$activeSolution.AppHost", "$activeSolution.ServiceDefaults")
    }

    @Test
    @Solution("SolutionWithMultipleAspNetCoreProjects")
    fun `Add aspire orchestration for multiple asp net core projects`() {
        val projectNames = listOf("Project1", "Project2", "Project3")
        runTest(projectNames, "$activeSolution.AppHost", "$activeSolution.ServiceDefaults")
    }

    @Test
    @Solution("DefaultAspNetCoreSolutionWithAppHost")
    fun `Add aspire orchestration for asp net core project with existing AppHost and ServiceDefaults`() {
        runTest(listOf(activeSolution), "AppHost", "ServiceDefaults")
    }

    @Test
    @Solution("DefaultWorkerSolution")
    fun `Add aspire orchestration for worker project`() {
        runTest(listOf(activeSolution), "$activeSolution.AppHost", "$activeSolution.ServiceDefaults")
    }

    private fun runTest(projectNames: List<String>, appHostName: String, serviceDefaultsName: String) {
        prepareProjectView(project)

        val solutionDirectoryPath = activeSolutionDirectory.toPath()
        val solution = activeSolutionDirectory.resolve("$activeSolution.sln")
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
                printStream.printProjectFiles(projectPath)
            }
        }
    }

    private fun PrintStream.printAspireFiles(solution: File, hostProjectPath: Path, sharedProjectPath: Path) {
        println("Solution file:")
        println()
        println(solution.readText())
        println()
        println("AppHost:")
        println()
        println(hostProjectPath.readText())
        println()
        println("AppHost.Program.cs:")
        println()
        println(hostProjectPath.parent.resolve("Program.cs").readText())
        println()
        println("ServiceDefaults:")
        println()
        println(sharedProjectPath.readText())
        println()
    }

    private fun PrintStream.printProjectFiles(projectPath: Path) {
        println("Project(${projectPath.name}):")
        println()
        println(projectPath.readText())
        println()
        println("Project(${projectPath.name}) Program.cs:")
        println()
        println(projectPath.parent.resolve("Program.cs").readText())
        println()
    }
}