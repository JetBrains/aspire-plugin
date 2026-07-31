package com.jetbrains.aspire

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.rider.util.parseTargetFrameworkId
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetFrameworkParsingTests {
    @BeforeClass
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @DataProvider(name = "supportedTargetFrameworks")
    fun supportedTargetFrameworks(): Array<Array<Any>> = arrayOf(
        arrayOf("net10.0", 10, 0, 0),
        arrayOf("net10.0-windows", 10, 0, 0),
        arrayOf("net8.0-windows10.0.19041.0", 8, 0, 0),
        arrayOf("net9.0-android", 9, 0, 0),
        arrayOf("net9.0-ios15.0", 9, 0, 0),
        arrayOf("netcoreapp3.1", 3, 1, 0),
    )

    @Test(dataProvider = "supportedTargetFrameworks")
    fun `A target framework should be parsed`(
        targetFramework: String,
        major: Int,
        minor: Int,
        patch: Int
    ) {
        val targetFrameworkId = parseTargetFrameworkId(targetFramework)

        assertNotNull(targetFrameworkId, "Unable to parse target framework $targetFramework")
        assertEquals(major, targetFrameworkId.version.major)
        assertEquals(minor, targetFrameworkId.version.minor)
        assertEquals(patch, targetFrameworkId.version.patch)
        assertEquals(targetFramework, targetFrameworkId.presentableName)
        assertEquals(".NETCoreApp", targetFrameworkId.shortName)
        assertTrue(targetFrameworkId.isNetCoreApp)
    }

    @Test
    fun `A target framework should be trimmed before parsing`() {
        val targetFrameworkId = parseTargetFrameworkId("  net10.0-windows\r\n")

        assertNotNull(targetFrameworkId)
        assertEquals("net10.0-windows", targetFrameworkId.presentableName)
        assertEquals(10, targetFrameworkId.version.major)
    }

    @DataProvider(name = "unsupportedTargetFrameworks")
    fun unsupportedTargetFrameworks(): Array<Array<Any>> = arrayOf(
        arrayOf("netstandard2.1"),
        arrayOf("wpa81"),
        arrayOf("net10.0-windows-extra"),
        arrayOf("garbage"),
        arrayOf("net"),
        arrayOf(""),
    )

    @Test(dataProvider = "unsupportedTargetFrameworks")
    fun `An unsupported target framework should not be parsed`(targetFramework: String) {
        assertNull(parseTargetFrameworkId(targetFramework), "Unexpectedly parsed $targetFramework")
    }
}
