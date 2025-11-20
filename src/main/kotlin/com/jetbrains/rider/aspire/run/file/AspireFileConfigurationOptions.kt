package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.configurations.RunConfigurationOptions

internal class AspireFileConfigurationOptions: RunConfigurationOptions() {
    var filePath by string()
    var arguments by string()
    var workingDirectory by string()
    var envs by map<String, String>()
    var usePodmanRuntime by property(false)
}