package com.jetbrains.aspire.rider.run

import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfigurationParameters {
    val mainFilePath: String
    val usePodmanRuntime: Boolean
    val startBrowserParameters: DotNetStartBrowserParameters
}