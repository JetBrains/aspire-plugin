package com.jetbrains.rider.aspire.templates

import com.jetbrains.rider.test.base.ProjectTemplateBaseTest
import com.jetbrains.rider.test.scriptingApi.ProjectTemplates.Sdk.Core31.VB.sdkTemplate
import com.jetbrains.rider.test.scriptingApi.TemplateIdWithVersion
import org.testng.annotations.Test

class AspireXUnitProjectTemplateTest : ProjectTemplateBaseTest() {
    override val templateId: TemplateIdWithVersion
        get() = sdkTemplate("Aspire.Tests.xUnit.CSharp.8.0", false)
    override val projectName: String
        get() = "AspireXUnit"

    @Test
    fun test() {
    }
}