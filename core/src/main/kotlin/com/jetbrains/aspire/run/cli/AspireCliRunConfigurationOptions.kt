package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunConfigurationOptions

/**
 * Persisted state of an [AspireCliRunConfiguration].
 *
 * Each property is a [com.intellij.openapi.components.BaseState] stored-property delegate, so the property
 * name is also the serialization name. Do not wrap these in separate accessor properties: the serializer
 * would then write the accessor name while restoring into the delegate name, and the value would be lost
 * on reload (this is what happened for the environment-variables map).
 *
 * [arguments], [workingDirectory], [envs] and [passParentEnvs] back the
 * [com.intellij.execution.CommonProgramRunConfigurationParameters] implemented by the configuration,
 * so the standard fragment editors ([com.intellij.execution.ui.CommonParameterFragments]) can drive them.
 */
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
