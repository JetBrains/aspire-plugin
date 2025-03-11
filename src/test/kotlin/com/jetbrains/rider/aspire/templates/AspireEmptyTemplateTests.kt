package com.jetbrains.rider.aspire.templates

import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.base.ProjectTemplateBaseTest
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.ProjectTemplates
import com.jetbrains.rider.test.scriptingApi.checkCanExecuteSelectedRunConfiguration
import com.jetbrains.rider.test.scriptingApi.doTestDumpRunConfigurationsFromRunManager
import org.testng.annotations.Test

@TestEnvironment(sdkVersion = SdkVersion.DOT_NET_8)
class AspireEmptyTemplateTests: ProjectTemplateBaseTest() {
    override val projectName = "AspireEmpty"
    override val templateId = ProjectTemplates.Sdk.Net8.aspireEmpty

    @Test(dependsOnMethods = ["createTemplateProject"])
    fun runConfiguration() {
        checkCanExecuteSelectedRunConfiguration(project)
        executeWithGold(configGoldFile) { printStream ->
            doTestDumpRunConfigurationsFromRunManager(project, printStream, maskExeExtension = true, maskPorts = true)
        }
    }
}