package com.jetbrains.aspire.rider.processLaunchers

import com.intellij.ide.browsers.StartBrowserSettings
import com.jetbrains.rider.runtime.DotNetExecutable

/**
 * An executable that runs an Aspire session.
 *
 * @property canRunUnderDebugger `true` if the executable is started under the debugger in a debug session.
 * `false` if the executable doesn't run the project output (e.g., MSBuild resolved a dev server command),
 * so a debug session falls back to a run session.
 */
internal data class SessionExecutable(
    val executable: DotNetExecutable,
    val browserSettings: StartBrowserSettings? = null,
    val canRunUnderDebugger: Boolean = true,
)
