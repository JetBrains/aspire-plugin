package com.jetbrains.aspire.run.cli

import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the argument list for `aspire run`.
 */
@ApiStatus.Internal
object AspireCliArguments {
    /**
     * Structured options are placed before [userArguments], so a duplicate typed by the user
     * is the later occurrence and therefore wins.
     */
    fun buildRunArguments(
        appHostFilePath: String,
        noBuild: Boolean = false,
        isolated: Boolean = false,
        logLevel: AspireCliLogLevel? = null,
        userArguments: String? = null
    ): List<String> = buildList {
        add("run")
        add("--nologo")
        add("--non-interactive")
        add("--apphost")
        add(appHostFilePath)
        if (noBuild) add("--no-build")
        if (isolated) add("--isolated")
        logLevel?.let {
            add("--log-level")
            add(it.name)
        }
        userArguments?.takeIf { it.isNotBlank() }?.let { addAll(ParametersListUtil.parse(it)) }
    }
}
