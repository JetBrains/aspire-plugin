package com.jetbrains.rider.aspire

import com.intellij.execution.RunManagerEx
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationType
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.maskMachineSpecificPaths
import org.testng.annotations.Test

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("DefaultAspireSolution")
class RunConfigurationTests : PerClassSolutionTestBase() {

    @Test
    fun `A run configuration should be generated for an opened solution`() {
        val runManagerEx = RunManagerEx.getInstanceEx(project)
        val allSettings = runManagerEx.allSettings.filter { it.type.id == AspireHostConfigurationType.ID }
        executeWithGold(testGoldFile) { printStream ->
            printStream.println("Aspire Host run configuration count: ${allSettings.size}")
            allSettings.map { it.configuration }.filterIsInstance<AspireHostConfiguration>().forEach {
                printStream.println("---")
                printStream.println("Name: ${it.name}")
                printStream.println("Type: ${it.type.displayName}")
                with(it.parameters) {
                    printStream.println("Project filePath: ${maskMachineSpecificPaths(project, projectFilePath)}")
                    printStream.println("Project TFM: $projectTfm")
                    printStream.println("Launch settings profile name: $profileName")
                    printStream.println("Arguments: ${maskMachineSpecificPaths(project, arguments)}")
                    printStream.println("Working directory: ${maskMachineSpecificPaths(project, workingDirectory)}")
                    dumpEnvironment(project, "Environment variables", envs, printStream)
                    printStream.println("Use Podman: $usePodmanRuntime")
                    dumpStartBrowserParameters(startBrowserParameters, printStream)
                }
            }
        }
    }
}