package com.jetbrains.aspire.dashboard

import com.intellij.execution.process.ProcessHandler

/**
 * A no-op [ProcessHandler] used solely as a sink for console log output.
 *
 * The terminal console requires a [ProcessHandler] to attach to; this implementation
 * provides one that does not manage any real process.
 */
internal class LogProcessHandler : ProcessHandler() {
    override fun destroyProcessImpl() {}
    override fun detachProcessImpl() {}
    override fun detachIsDefault() = false
    override fun getProcessInput() = null
}
