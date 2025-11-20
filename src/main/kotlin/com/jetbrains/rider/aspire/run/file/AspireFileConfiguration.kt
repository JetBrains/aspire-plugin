package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.run.configurations.IAutoSelectableRunConfiguration

internal class AspireFileConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<AspireFileConfigurationOptions>(
    project,
    factory,
    name
), IAutoSelectableRunConfiguration {
    override fun checkConfiguration() {
        super.checkConfiguration()
    }

    override fun getConfigurationEditor() = AspireFileConfigurationSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        throw UnsupportedOperationException("Synchronous call to getState is not supported by ${AspireFileConfigurationFactory::class.java}")
    }

    suspend fun getRunProfileStateAsync(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val lifetime = AspireService.getInstance(project).lifetime.createNested()
        return AspireFileExecutorFactory().create(executor.id, environment, lifetime)
    }

    override fun getAutoSelectPriority() = 10
}