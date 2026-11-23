package com.jetbrains.aspire.unit.orchestration

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.rider.orchestration.TemplateCandidate
import com.jetbrains.aspire.rider.orchestration.TemplateFrameworkSelectionStrategy
import com.jetbrains.aspire.rider.orchestration.TemplateSelection
import com.jetbrains.aspire.rider.orchestration.isCompatibleWithLibraryFrameworks
import com.jetbrains.aspire.rider.orchestration.parseTemplateIdVersion
import com.jetbrains.aspire.rider.orchestration.selectLatestTemplate
import com.jetbrains.aspire.rider.orchestration.selectTemplate
import com.jetbrains.aspire.rider.orchestration.selectTemplateFramework
import com.jetbrains.rider.model.RdVersionInfo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateFrameworkSelectorTest {
    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @Test
    fun `Lowest strategy selects the lowest supported project framework`() {
        val targetFrameworks = listOf("net8.0", "net10.0")
        val supportedFrameworks = setOf("net8.0", "net9.0", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertEquals("net8.0", result)
    }

    @Test
    fun `Highest strategy selects the highest supported project framework`() {
        val targetFrameworks = listOf("net8.0", "net10.0")
        val supportedFrameworks = setOf("net8.0", "net9.0", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Highest
        )

        assertEquals("net10.0", result)
    }

    @Test
    fun `Project framework suffixes are dropped when selecting a template choice`() {
        val targetFrameworks = listOf("net10.0-android", "net10.0-ios")
        val supportedFrameworks = setOf("net10.0-android", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertEquals("net10.0", result)
    }

    @Test
    fun `Platform specific choices are skipped during fallback`() {
        val targetFrameworks = listOf("net10.0-windows")
        val supportedFrameworks = setOf("net9.0-windows", "net10.0-windows", "net11.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertEquals("net11.0", result)
    }

    @Test
    fun `Only platform specific template choices return null`() {
        val targetFrameworks = listOf("net10.0-windows")
        val supportedFrameworks = setOf("net10.0-windows")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertNull(result)
    }

    @Test
    fun `Empty project frameworks return null`() {
        val targetFrameworks = emptyList<String>()
        val supportedFrameworks = setOf("net8.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertNull(result)
    }

    @Test
    fun `Highest strategy selects the nearest lower supported framework`() {
        val targetFrameworks = listOf("net9.0", "net12.0")
        val supportedFrameworks = setOf("net10.0", "net11.0", "net13.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Highest
        )

        assertEquals("net11.0", result)
    }

    @Test
    fun `Highest strategy selects the nearest higher supported framework if there is no lower one`() {
        val targetFrameworks = listOf("net8.0", "net9.0")
        val supportedFrameworks = setOf("net11.0", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Highest
        )

        assertEquals("net10.0", result)
    }

    @Test
    fun `Unparseable supported frameworks return null`() {
        val targetFrameworks = listOf("net9.0")
        val supportedFrameworks = setOf("netstandard2.0", "garbage")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Highest
        )

        assertNull(result)
    }

    @Test
    fun `Empty supported frameworks return null`() {
        val targetFrameworks = listOf("net8.0")
        val supportedFrameworks = emptySet<String>()

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertNull(result)
    }

    @Test
    fun `Unparseable and legacy frameworks are ignored`() {
        val targetFrameworks = listOf("netstandard2.1", "net48", "garbage", "net8.0")
        val supportedFrameworks = setOf("net8.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Highest
        )

        assertEquals("net8.0", result)
    }

    @Test
    fun `Lowest strategy selects the nearest higher supported framework if there is no lower one`() {
        val targetFrameworks = listOf("net9.0", "net10.0", "net11.0")
        val supportedFrameworks = setOf("net11.0", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertEquals("net10.0", result)
    }

    @Test
    fun `Lowest strategy prefers the nearest lower supported framework when both sides are available`() {
        val targetFrameworks = listOf("net9.0", "net10.0")
        val supportedFrameworks = setOf("net7.0", "net8.0", "net10.0")

        val result = selectTemplateFramework(
            targetFrameworks,
            supportedFrameworks,
            TemplateFrameworkSelectionStrategy.Lowest
        )

        assertEquals("net8.0", result)
    }

    @Test
    fun `Project with a lower framework is incompatible with library`() {
        val projectFrameworks = listOf("net9.0")
        val libraryFrameworks = listOf("net10.0")

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertFalse(result)
    }

    @Test
    fun `Project with the same or higher framework is compatible with library`() {
        val projectFrameworks = listOf("net10.0", "net11.0")
        val libraryFrameworks = listOf("net10.0")

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertTrue(result)
    }

    @Test
    fun `Multi-targeted project with one lower framework is incompatible with library`() {
        val projectFrameworks = listOf("net9.0", "net10.0")
        val libraryFrameworks = listOf("net10.0")

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertFalse(result)
    }

    @Test
    fun `Platform specific project frameworks are compatible with library`() {
        val projectFrameworks = listOf("net10.0-android", "net10.0-ios")
        val libraryFrameworks = listOf("net10.0")

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertTrue(result)
    }

    @Test
    fun `Unknown project frameworks are compatible with library`() {
        val projectFrameworks = emptyList<String>()
        val libraryFrameworks = listOf("net10.0")

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertTrue(result)
    }

    @Test
    fun `Unknown library frameworks are compatible with project`() {
        val projectFrameworks = listOf("net9.0")
        val libraryFrameworks = emptyList<String>()

        val result = isCompatibleWithLibraryFrameworks(projectFrameworks, libraryFrameworks)

        assertTrue(result)
    }

    @Test
    fun `Template ID version suffix is parsed`() {
        val templateId = "MauiAspire.ServiceDefaults.CSharp.11.0"

        val result = parseTemplateIdVersion(templateId, MAUI_SERVICE_DEFAULTS_TEMPLATE_ID)

        assertEquals(RdVersionInfo(11, 0, 0), result)
    }

    @Test
    fun `Template ID without a version suffix has no version`() {
        val templateId = "MauiAspire.ServiceDefaults.CSharp.Preview"

        val result = parseTemplateIdVersion(templateId, MAUI_SERVICE_DEFAULTS_TEMPLATE_ID)

        assertNull(result)
    }

    @Test
    fun `Latest template is selected by numeric version`() {
        val net10Template = TemplateCandidate(NET10_MAUI_TEMPLATE_ID, RdVersionInfo(8, 0, 0), setOf("net10.0"))
        val net11Template = TemplateCandidate(NET11_MAUI_TEMPLATE_ID, RdVersionInfo(11, 0, 0), setOf("net11.0"))
        val candidates = listOf(net10Template, net11Template)

        val result = selectLatestTemplate(candidates)

        assertEquals(net11Template, result)
    }

    @Test
    fun `Template without a version is not selected as latest`() {
        val unversionedTemplate = TemplateCandidate("MauiAspire.ServiceDefaults.CSharp", null, setOf("net11.0"))
        val net10Template = TemplateCandidate(NET10_MAUI_TEMPLATE_ID, RdVersionInfo(8, 0, 0), setOf("net10.0"))
        val candidates = listOf(unversionedTemplate, net10Template)

        val result = selectLatestTemplate(candidates)

        assertEquals(net10Template, result)
    }

    @Test
    fun `Template supporting the project framework is selected among several templates`() {
        val net10Template = TemplateCandidate(NET10_MAUI_TEMPLATE_ID, RdVersionInfo(8, 0, 0), setOf("net10.0"))
        val net11Template = TemplateCandidate(NET11_MAUI_TEMPLATE_ID, RdVersionInfo(11, 0, 0), setOf("net11.0"))
        val candidates = listOf(net10Template, net11Template)
        val targetFrameworks = listOf("net11.0-android")

        val result = selectTemplate(candidates, targetFrameworks, TemplateFrameworkSelectionStrategy.Lowest)

        assertEquals(TemplateSelection(NET11_MAUI_TEMPLATE_ID, "net11.0"), result)
    }

    @Test
    fun `Older template is selected for an older project framework`() {
        val net10Template = TemplateCandidate(NET10_MAUI_TEMPLATE_ID, RdVersionInfo(8, 0, 0), setOf("net10.0"))
        val net11Template = TemplateCandidate(NET11_MAUI_TEMPLATE_ID, RdVersionInfo(11, 0, 0), setOf("net11.0"))
        val candidates = listOf(net10Template, net11Template)
        val targetFrameworks = listOf("net10.0-android")

        val result = selectTemplate(candidates, targetFrameworks, TemplateFrameworkSelectionStrategy.Lowest)

        assertEquals(TemplateSelection(NET10_MAUI_TEMPLATE_ID, "net10.0"), result)
    }

    @Test
    fun `Latest template is selected when several templates support the framework`() {
        val olderTemplate =
            TemplateCandidate(NET10_MAUI_TEMPLATE_ID, RdVersionInfo(8, 0, 0), setOf("net10.0"))
        val newerTemplate =
            TemplateCandidate(NET11_MAUI_TEMPLATE_ID, RdVersionInfo(11, 0, 0), setOf("net10.0", "net11.0"))
        val candidates = listOf(olderTemplate, newerTemplate)
        val targetFrameworks = listOf("net10.0")

        val result = selectTemplate(candidates, targetFrameworks, TemplateFrameworkSelectionStrategy.Lowest)

        assertEquals(TemplateSelection(NET11_MAUI_TEMPLATE_ID, "net10.0"), result)
    }

    @Test
    fun `No template is selected for unrecognized project frameworks`() {
        val net11Template = TemplateCandidate(NET11_MAUI_TEMPLATE_ID, RdVersionInfo(11, 0, 0), setOf("net11.0"))
        val candidates = listOf(net11Template)
        val targetFrameworks = listOf("net48")

        val result = selectTemplate(candidates, targetFrameworks, TemplateFrameworkSelectionStrategy.Lowest)

        assertNull(result)
    }

    private companion object {
        const val MAUI_SERVICE_DEFAULTS_TEMPLATE_ID = "MauiAspire.ServiceDefaults.CSharp"
        const val NET10_MAUI_TEMPLATE_ID = "MauiAspire.ServiceDefaults.CSharp.8.0"
        const val NET11_MAUI_TEMPLATE_ID = "MauiAspire.ServiceDefaults.CSharp.11.0"
    }
}
