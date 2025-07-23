package com.jetbrains.rider.aspire

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.aspire.orchestration.AspireOrchestrationService
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.base.ProjectModelBaseTest
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.prepareProjectView
import com.jetbrains.rider.test.scriptingApi.runBlockingWithFlushing
import org.testng.annotations.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class AspireOrchestrationTests : ProjectModelBaseTest() {
    @Test
    @Solution("DefaultAspNetCoreSolution")
    fun `Add aspire orchestration for asp net core project`() {
        prepareProjectView(project)

        val solutionDirectoryPath = activeSolutionDirectory.toPath()
        val solution = activeSolutionDirectory.resolve("$activeSolution.sln")
        val projectPath = solutionDirectoryPath.resolve(activeSolution).resolve("$activeSolution.csproj")

        val service = AspireOrchestrationService.getInstance(project)
        runBlockingWithFlushing("Adding .NET Aspire Orchestration", 5.minutes) {
            val projectEntity = project.serviceAsync<WorkspaceModel>()
                .findProjects()
                .singleOrNull { it.url?.toPath() == projectPath }
            requireNotNull(projectEntity) { "No project entity found for $projectPath" }

            service.addAspireOrchestration(listOf(projectEntity))
        }

        val hostProjectFileName = "$activeSolution.AppHost"
        val hostProjectPath = solutionDirectoryPath.resolve(hostProjectFileName).resolve("$hostProjectFileName.csproj")
        hostProjectPath.exists().shouldBeTrue()

        val sharedProjectFileName = "$activeSolution.ServiceDefaults"
        val sharedProjectPath = solutionDirectoryPath.resolve(sharedProjectFileName).resolve("$sharedProjectFileName.csproj")
        sharedProjectPath.exists().shouldBeTrue()

        executeWithGold(testGoldFile) { printStream ->
            printStream.println("Sln:")
            printStream.println()
            printStream.println(solution.readText())
            printStream.println("AppHost:")
            printStream.println()
            printStream.println(hostProjectPath.readText())
            printStream.println("AppHost.AppHost.cs:")
            printStream.println()
            printStream.println(hostProjectPath.parent.resolve("AppHost.cs").readText())
            printStream.println("ServiceDefaults:")
            printStream.println()
            printStream.println(sharedProjectPath.readText())
            printStream.println("Project:")
            printStream.println()
            printStream.println(projectPath.readText())
            printStream.println("Project.Program.cs:")
            printStream.println()
            printStream.println(projectPath.parent.resolve("Program.cs").readText())
        }
    }
}