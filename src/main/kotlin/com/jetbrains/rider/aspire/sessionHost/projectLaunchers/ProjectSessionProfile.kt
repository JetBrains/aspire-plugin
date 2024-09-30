package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import icons.RiderIcons
import java.nio.file.Path

abstract class ProjectSessionProfile(
    private val projectName: String,
    val dotnetExecutable: DotNetExecutable,
    val aspireHostProjectPath: Path?
) : RunProfile {
    override fun getName() = projectName

    override fun getIcon() = RiderIcons.RunConfigurations.DotNetProject
}