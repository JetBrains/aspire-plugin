package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XMap

internal class AspireCliRunConfigurationOptions : RunConfigurationOptions() {
    @get:OptionTag("appHostFilePath")
    var appHostFilePath: String? by string()

    @get:OptionTag("workingDirectory")
    var workingDirectory: String? by string()

    @get:OptionTag("browserUrl")
    var browserUrl: String? by string()

    @get:OptionTag("startBrowserAfterLaunch")
    var startBrowserAfterLaunch: Boolean by property(false)

    @get:OptionTag("noBuild")
    var noBuild: Boolean by property(false)

    @get:OptionTag("isolated")
    var isolated: Boolean by property(false)

    @get:OptionTag("logLevel")
    var logLevel: AspireCliLogLevel? by enum<AspireCliLogLevel>()

    @get:OptionTag("usePodmanRuntime")
    var usePodmanRuntime: Boolean by property(false)

    @get:OptionTag("passSystemEnvironment")
    var passSystemEnvironment: Boolean by property(true)

    @get:XMap(propertyElementName = "envs", entryTagName = "env", keyAttributeName = "name")
    var environmentVariables: MutableMap<String, String> by map()
}
