package com.jetbrains.aspire

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.run.cli.AspireCliArguments
import com.jetbrains.aspire.run.cli.AspireCliLogLevel
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import kotlin.test.assertEquals

class AspireCliArgumentsTests {
    companion object {
        private const val APP_HOST = "/tmp/AppHost/AppHost.csproj"

        private val BASE = listOf("run", "--nologo", "--non-interactive", "--apphost", APP_HOST)
    }

    @BeforeClass
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @Test
    fun `Only the base arguments should be built by default`() {
        val arguments = AspireCliArguments.buildRunArguments(APP_HOST)

        @Suppress("KotlinMisorderedAssertEqualsArguments")
        assertEquals(BASE, arguments)
    }

    @Test
    fun `The CLI should always be run non-interactively`() {
        val arguments = AspireCliArguments.buildRunArguments(
            APP_HOST,
            noBuild = true,
            isolated = true,
            logLevel = AspireCliLogLevel.Trace,
            waitForDebugger = true,
            userArguments = "--foo"
        )

        assertEquals(true, arguments.contains("--non-interactive"))
        assertEquals(1, arguments.count { it == "--non-interactive" })
    }

    @DataProvider(name = "flags")
    fun flags(): Array<Array<Any>> = arrayOf(
        arrayOf("--no-build", { it: String -> AspireCliArguments.buildRunArguments(it, noBuild = true) }),
        arrayOf("--isolated", { it: String -> AspireCliArguments.buildRunArguments(it, isolated = true) }),
        arrayOf("--wait-for-debugger", { it: String -> AspireCliArguments.buildRunArguments(it, waitForDebugger = true) })
    )

    @Test(dataProvider = "flags")
    fun `A flag should be passed only when it is enabled`(
        flag: String,
        enabled: (String) -> List<String>
    ) {
        assertEquals(BASE + flag, enabled(APP_HOST))
        assertEquals(false, AspireCliArguments.buildRunArguments(APP_HOST).contains(flag))
    }

    @Test
    fun `All enabled flags should be passed together`() {
        val arguments = AspireCliArguments.buildRunArguments(
            APP_HOST,
            noBuild = true,
            isolated = true,
            logLevel = AspireCliLogLevel.Debug,
            waitForDebugger = true
        )

        @Suppress("KotlinMisorderedAssertEqualsArguments")
        assertEquals(
            BASE + listOf("--no-build", "--isolated", "--log-level", "Debug", "--wait-for-debugger"),
            arguments
        )
    }

    @Test
    fun `The log level should not be passed when it is not set`() {
        val arguments = AspireCliArguments.buildRunArguments(APP_HOST, logLevel = null)

        assertEquals(false, arguments.contains("--log-level"))
    }

    @DataProvider(name = "logLevels")
    fun logLevels(): Array<Array<Any>> = AspireCliLogLevel.entries
        .map { arrayOf<Any>(it, it.name) }
        .toTypedArray()

    @Test(dataProvider = "logLevels")
    fun `The log level should be passed by its name`(logLevel: AspireCliLogLevel, expected: String) {
        val arguments = AspireCliArguments.buildRunArguments(APP_HOST, logLevel = logLevel)

        assertEquals(BASE + listOf("--log-level", expected), arguments)
    }

    @DataProvider(name = "userArguments")
    fun userArguments(): Array<Array<Any?>> = arrayOf(
        arrayOf(null, emptyList<String>()),
        arrayOf("", emptyList<String>()),
        arrayOf("   ", emptyList<String>()),
        arrayOf("--foo", listOf("--foo")),
        arrayOf("""--foo "a b"""", listOf("--foo", "a b")),
        arrayOf("-- --forwarded", listOf("--", "--forwarded"))
    )

    @Test(dataProvider = "userArguments")
    fun `User arguments should be appended last`(userArguments: String?, expected: List<String>) {
        val arguments = AspireCliArguments.buildRunArguments(
            APP_HOST,
            noBuild = true,
            userArguments = userArguments
        )

        assertEquals(BASE + "--no-build" + expected, arguments)
    }
}
