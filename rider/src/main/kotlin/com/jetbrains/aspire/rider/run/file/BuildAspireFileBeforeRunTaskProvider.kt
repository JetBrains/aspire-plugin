@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.run.file

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rd.util.threading.coroutines.nextTrueValue
import com.jetbrains.rdclient.client.frontendProjectSession
import com.jetbrains.rdclient.client.protocol
import com.jetbrains.rider.RiderBundle
import com.jetbrains.rider.build.BuildHost
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.RiderBuildBundle
import com.jetbrains.rider.build.tasks.BeforeRunTaskWithProjectProvider
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.model.BuildTarget
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import kotlin.io.path.pathString

internal class BuildAspireFileBeforeRunTaskProvider : BeforeRunTaskWithProjectProvider<BuildAspireFileBeforeRunTask>() {
    companion object {

        val LOG = logger<BuildAspireFileBeforeRunTaskProvider>()
    }

    override val baseDescription: @Nls String
        get() = RiderBuildBundle.message("description.build.project")

    override fun getId(): Key<BuildAspireFileBeforeRunTask> = providerId

    override fun getName(): String = TASK_NAME

    override fun getIcon(): Icon = AllIcons.Actions.Compile

    override fun shouldCreateBuildBeforeRunTaskByDefault(runConfiguration: RunConfiguration): Boolean {
        return runConfiguration is AspireFileConfiguration
    }

    override fun createTask(configuration: RunConfiguration): BuildAspireFileBeforeRunTask? {
        if (!shouldCreateBuildBeforeRunTaskByDefault(configuration)) return null

        return BuildAspireFileBeforeRunTask().apply {
            isEnabled = true
        }
    }

    override fun canExecuteTask(configuration: RunConfiguration, task: BuildAspireFileBeforeRunTask): Boolean {
        return BuildHost.getInstance(configuration.project).ready.value
    }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        env: ExecutionEnvironment,
        task: BuildAspireFileBeforeRunTask
    ): Boolean {
        if (configuration !is AspireFileConfiguration) {
            LOG.warn("configuration is not AspireFileConfiguration")
            return false
        }

        val buildHost = BuildHost.getInstance(configuration.project)
        if (!buildHost.ready.value) {
            LOG.warn("buildHost is not ready")
            return false
        }

        return Lifetime.using { buildSessionLt ->
            LOG.runAndLogException {
                return@runAndLogException runBlockingMaybeCancellable {
                    return@runBlockingMaybeCancellable buildWithProgress(env, configuration, buildSessionLt)
                }
            } == true
        }
    }

    private suspend fun buildWithProgress(
        env: ExecutionEnvironment,
        configuration: AspireFileConfiguration,
        buildSessionLt: Lifetime,
    ): Boolean = withBackgroundProgress(env.project, RiderBundle.message("progress.title.generating.project.file")) {
        val projectToBuildPath = configuration.tryGetProjectFilePath(buildSessionLt)

        if (projectToBuildPath == null) {
            LOG.warn("projectToBuildPath is null")
            return@withBackgroundProgress false
        }

        val selectedProjectsForBuild = listOf(projectToBuildPath.pathString)
        val buildTaskThrottler = BuildTaskThrottler.getInstance(configuration.project)

        val project = env.project
        val session = project.frontendProjectSession.appSession
        val protocol = session.protocol

        withContext(protocol.scheduler.asCoroutineDispatcher) {
            BuildHost.getInstance(project).buildModel.ready.nextTrueValue()
        }

        val buildStatus = buildTaskThrottler.buildSequentially(
            BuildParameters(BuildTarget(), selectedProjectsForBuild, isSingleProjectBuild = true)
        )
        return@withBackgroundProgress buildStatus.msBuildStatus
    }
}

internal val providerId = Key.create<BuildAspireFileBeforeRunTask>("BuildAspireFile")

@Nls
private val TASK_NAME = RiderBuildBundle.message("BuildProjectBeforeRunTaskProvider.task.name.build.project")