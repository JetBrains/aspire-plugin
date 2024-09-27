package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import icons.RiderIcons

abstract class ProjectSessionRunProfile(private val projectName: String) : RunProfile {
    override fun getName() = projectName

    override fun getIcon() = RiderIcons.RunConfigurations.DotNetProject
}