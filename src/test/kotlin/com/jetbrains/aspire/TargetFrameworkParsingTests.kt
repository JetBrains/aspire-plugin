package com.jetbrains.aspire

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.rider.util.parseTargetFrameworkId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TargetFrameworkParsingTests {
    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    fun supportedTargetFrameworks(): Stream<Arguments> = Stream.of(
        Arguments.of("net10.0", 10, 0, 0),
        Arguments.of("net10.0-windows", 10, 0, 0),
        Arguments.of("net8.0-windows10.0.19041.0", 8, 0, 0),
        Arguments.of("net9.0-android", 9, 0, 0),
        Arguments.of("net9.0-ios15.0", 9, 0, 0),
        Arguments.of("netcoreapp3.1", 3, 1, 0),
    )

    @ParameterizedTest
    @MethodSource("supportedTargetFrameworks")
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

    fun unsupportedTargetFrameworks(): Stream<Arguments> = Stream.of(
        Arguments.of("netstandard2.1"),
        Arguments.of("wpa81"),
        Arguments.of("net10.0-windows-extra"),
        Arguments.of("garbage"),
        Arguments.of("net"),
        Arguments.of(""),
    )

    @ParameterizedTest
    @MethodSource("unsupportedTargetFrameworks")
    fun `An unsupported target framework should not be parsed`(targetFramework: String) {
        assertNull(parseTargetFrameworkId(targetFramework), "Unexpectedly parsed $targetFramework")
    }
}
