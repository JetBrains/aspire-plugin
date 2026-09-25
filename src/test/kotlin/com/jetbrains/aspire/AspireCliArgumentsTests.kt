package com.jetbrains.aspire

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.run.cli.AspireCliArguments
import com.jetbrains.aspire.run.cli.AspireCliLogLevel
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AspireCliArgumentsTests {
    companion object {
        private const val APP_HOST = "/tmp/AppHost/AppHost.csproj"

        private val BASE = listOf("run", "--nologo", "--non-interactive", "--apphost", APP_HOST)
    }

    @BeforeAll
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
            userArguments = "--foo"
        )

        assertEquals(true, arguments.contains("--non-interactive"))
        assertEquals(1, arguments.count { it == "--non-interactive" })
    }

    fun flags(): Stream<Arguments> = Stream.of(
        Arguments.of("--no-build", { it: String -> AspireCliArguments.buildRunArguments(it, noBuild = true) }),
        Arguments.of("--isolated", { it: String -> AspireCliArguments.buildRunArguments(it, isolated = true) })
    )

    @ParameterizedTest
    @MethodSource("flags")
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
            logLevel = AspireCliLogLevel.Debug
        )

        @Suppress("KotlinMisorderedAssertEqualsArguments")
        assertEquals(
            BASE + listOf("--no-build", "--isolated", "--log-level", "Debug"),
            arguments
        )
    }

    @Test
    fun `The log level should not be passed when it is not set`() {
        val arguments = AspireCliArguments.buildRunArguments(APP_HOST, logLevel = null)

        assertEquals(false, arguments.contains("--log-level"))
    }

    fun logLevels(): Stream<Arguments> = AspireCliLogLevel.entries
        .map { Arguments.of(it, it.name) }
        .stream()

    @ParameterizedTest
    @MethodSource("logLevels")
    fun `The log level should be passed by its name`(logLevel: AspireCliLogLevel, expected: String) {
        val arguments = AspireCliArguments.buildRunArguments(APP_HOST, logLevel = logLevel)

        assertEquals(BASE + listOf("--log-level", expected), arguments)
    }

    fun userArguments(): Stream<Arguments> = Stream.of(
        Arguments.of(null as String?, emptyList<String>()),
        Arguments.of("", emptyList<String>()),
        Arguments.of("   ", emptyList<String>()),
        Arguments.of("--foo", listOf("--foo")),
        Arguments.of("""--foo "a b"""", listOf("--foo", "a b")),
        Arguments.of("-- --forwarded", listOf("--", "--forwarded"))
    )

    @ParameterizedTest
    @MethodSource("userArguments")
    fun `User arguments should be appended last`(userArguments: String?, expected: List<String>) {
        val arguments = AspireCliArguments.buildRunArguments(
            APP_HOST,
            noBuild = true,
            userArguments = userArguments
        )

        assertEquals(BASE + "--no-build" + expected, arguments)
    }
}
