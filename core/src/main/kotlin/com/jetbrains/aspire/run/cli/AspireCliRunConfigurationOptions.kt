package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunConfigurationOptions

internal class AspireCliRunConfigurationOptions : RunConfigurationOptions() {
    var appHostFilePath by string()
    var arguments by string()
    var workingDirectory by string()
    var aspireCliPath by string()
    var browserUrl by string()
    var startBrowserAfterLaunch by property(false)
    var enableIdeDebugging by property(false)
    var passParentEnvs by property(true)
    val envs by linkedMap<String, String>()
}
