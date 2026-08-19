package com.jetbrains.aspire.run.cli

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle
import kotlin.io.path.Path
import kotlin.io.path.exists

internal class AspireCliRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<AspireCliRunConfigurationOptions>(project, factory, name),
    CommonProgramRunConfigurationParameters {

    internal val cliOptions: AspireCliRunConfigurationOptions
        get() = options as AspireCliRunConfigurationOptions

    override fun getOptionsClass(): Class<AspireCliRunConfigurationOptions> =
        AspireCliRunConfigurationOptions::class.java

    var appHostFilePath: String?
        get() = cliOptions.appHostFilePath
        set(value) {
            cliOptions.appHostFilePath = value
        }

    var aspireCliPath: String?
        get() = cliOptions.aspireCliPath
        set(value) {
            cliOptions.aspireCliPath = value
        }

    var browserUrl: String?
        get() = cliOptions.browserUrl
        set(value) {
            cliOptions.browserUrl = value
        }

    var startBrowserAfterLaunch: Boolean
        get() = cliOptions.startBrowserAfterLaunch
        set(value) {
            cliOptions.startBrowserAfterLaunch = value
        }

    var enableIdeDebugging: Boolean
        get() = cliOptions.enableIdeDebugging
        set(value) {
            cliOptions.enableIdeDebugging = value
        }

    var noBuild: Boolean
        get() = cliOptions.noBuild
        set(value) {
            cliOptions.noBuild = value
        }

    var isolated: Boolean
        get() = cliOptions.isolated
        set(value) {
            cliOptions.isolated = value
        }

    var logLevel: AspireCliLogLevel?
        get() = cliOptions.logLevel
        set(value) {
            cliOptions.logLevel = value
        }

    var waitForDebugger: Boolean
        get() = cliOptions.waitForDebugger
        set(value) {
            cliOptions.waitForDebugger = value
        }

    override fun getConfigurationEditor(): SettingsEditor<AspireCliRunConfiguration> =
        AspireCliSettingsEditor(this)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        AspireCliRunProfileState(this, environment)

    override fun checkConfiguration() {
        val path = appHostFilePath
        if (path.isNullOrBlank()) {
            throw RuntimeConfigurationError(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }
        if (!Path(path).exists()) {
            throw RuntimeConfigurationError(AspireCoreBundle.message("run.configuration.cli.error.app.host.not.found", path))
        }
    }


    override fun getProgramParameters(): String? = cliOptions.arguments

    override fun setProgramParameters(value: String?) {
        cliOptions.arguments = value
    }

    override fun getWorkingDirectory(): String? = cliOptions.workingDirectory

    override fun setWorkingDirectory(value: String?) {
        cliOptions.workingDirectory = value
    }

    override fun getEnvs(): MutableMap<String, String> = LinkedHashMap(cliOptions.envs)

    override fun setEnvs(envs: MutableMap<String, String>) {
        cliOptions.envs.clear()
        cliOptions.envs.putAll(envs)
    }

    override fun isPassParentEnvs(): Boolean = cliOptions.passParentEnvs

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        cliOptions.passParentEnvs = passParentEnvs
    }
}
