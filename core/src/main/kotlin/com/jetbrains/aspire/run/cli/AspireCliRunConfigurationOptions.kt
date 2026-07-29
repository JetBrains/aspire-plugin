package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunConfigurationOptions

/**
 * Persisted state of an [AspireCliRunConfiguration].
 *
 * [arguments], [workingDirectory], [envs] and [passParentEnvs] back the
 * [com.intellij.execution.CommonProgramRunConfigurationParameters] implemented by the configuration,
 * so the standard fragment editors ([com.intellij.execution.ui.CommonParameterFragments]) can drive them.
 */
internal class AspireCliRunConfigurationOptions : RunConfigurationOptions() {
    private var appHostFilePathState by string()
    private var argumentsState by string()
    private var workingDirectoryState by string()
    private var aspireCliPathState by string()
    private var browserUrlState by string()
    private var startBrowserAfterLaunchState by property(false)
    private var enableIdeDebuggingState by property(false)
    private var passParentEnvsState by property(true)
    private val envsMap by linkedMap<String, String>()

    var appHostFilePath: String?
        get() = appHostFilePathState
        set(value) {
            appHostFilePathState = value
        }

    var arguments: String?
        get() = argumentsState
        set(value) {
            argumentsState = value
        }

    var workingDirectory: String?
        get() = workingDirectoryState
        set(value) {
            workingDirectoryState = value
        }

    var aspireCliPath: String?
        get() = aspireCliPathState
        set(value) {
            aspireCliPathState = value
        }

    var browserUrl: String?
        get() = browserUrlState
        set(value) {
            browserUrlState = value
        }

    var startBrowserAfterLaunch: Boolean
        get() = startBrowserAfterLaunchState
        set(value) {
            startBrowserAfterLaunchState = value
        }

    var enableIdeDebugging: Boolean
        get() = enableIdeDebuggingState
        set(value) {
            enableIdeDebuggingState = value
        }

    var passParentEnvs: Boolean
        get() = passParentEnvsState
        set(value) {
            passParentEnvsState = value
        }

    var envs: Map<String, String>
        get() = envsMap
        set(value) {
            envsMap.clear()
            envsMap.putAll(value)
        }
}
