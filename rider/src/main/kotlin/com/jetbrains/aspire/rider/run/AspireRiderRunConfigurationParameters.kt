package com.jetbrains.aspire.rider.run

import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRiderRunConfigurationParameters {
    val appHostFilePath: String
    val usePodmanRuntime: Boolean
    val startBrowserParameters: DotNetStartBrowserParameters
}