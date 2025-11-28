package com.jetbrains.aspire.run

import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters

internal interface AspireRunConfigurationParameters {
    val mainFilePath: String
    val usePodmanRuntime: Boolean
    val startBrowserParameters: DotNetStartBrowserParameters
}