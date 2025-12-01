package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.aspire.sessions.SessionProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import icons.RiderIcons
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

abstract class DotNetSessionProfile(
    override val sessionId: String,
    override val projectPath: Path,
    val dotnetExecutable: DotNetExecutable,
    override val aspireHostProjectPath: Path?,
    override val isDebugMode: Boolean,
) : RunProfile, SessionProfile {
    override fun getName() = projectPath.nameWithoutExtension

    override fun getIcon() = RiderIcons.RunConfigurations.DotNetProject
}