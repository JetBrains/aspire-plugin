package com.jetbrains.aspire.run.cli

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle
import kotlin.io.path.Path
import kotlin.io.path.exists

internal class AspireCliRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<AspireCliRunConfigurationOptions>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {

    internal val cliOptions: AspireCliRunConfigurationOptions
        get() = options as AspireCliRunConfigurationOptions

    override fun getConfigurationEditor(): SettingsEditor<AspireCliRunConfiguration> =
        AspireCliSettingsEditor(this)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        AspireCliRunProfileState(this, environment)

    override fun checkConfiguration() {
        val path = cliOptions.appHostFilePath
        if (path.isNullOrBlank()) {
            throw RuntimeConfigurationError(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }
        if (!Path(path).exists()) {
            throw RuntimeConfigurationError(
                AspireCoreBundle.message(
                    "run.configuration.cli.error.app.host.not.found",
                    path
                )
            )
        }
    }
}
