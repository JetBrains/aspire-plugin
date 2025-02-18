package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import icons.RiderIcons
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

abstract class ProjectSessionProfile(
    val projectPath: Path,
    val dotnetExecutable: DotNetExecutable,
    val aspireHostProjectPath: Path?,
    val isDebugMode: Boolean,
) : RunProfile {
    override fun getName() = projectPath.nameWithoutExtension

    override fun getIcon() = RiderIcons.RunConfigurations.DotNetProject
}