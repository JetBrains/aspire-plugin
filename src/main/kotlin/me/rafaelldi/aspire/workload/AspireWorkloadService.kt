@file:Suppress("UnstableApiUsage", "LoggingSimilarMessage")

package me.rafaelldi.aspire.workload

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.settings.AspireConfigurable
import me.rafaelldi.aspire.util.WorkloadVersion
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class AspireWorkloadService(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireWorkloadService>()

        private val LOG = logger<AspireWorkloadService>()

        private const val CURRENT_VERSION = "8.0.0-preview.7.24251.11"

        private val aspireRegex = Regex("^aspire", RegexOption.MULTILINE)
        private val aspireVersionRegex = Regex("^aspire\\s+([\\w.\\-]+)", RegexOption.MULTILINE)
    }

    private val aspireActualVersion = WorkloadVersion(CURRENT_VERSION)

    fun checkForUpdate() {
        LOG.trace("Checking Aspire workload for update")
        scope.launch(Dispatchers.Default) {
            val dotnetPath = getDotnetPath() ?: "dotnet"

            val isAspireInstalled = isAspireWorkloadInstalled(dotnetPath)
            if (!isAspireInstalled) {
                LOG.info("Aspire workload isn't installed")
                return@launch
            }

            val isCurrentVersionInstalled = isCurrentVersionInstalled(dotnetPath)
            if (!isCurrentVersionInstalled) {
                LOG.trace("Current version $aspireActualVersion isn't installed")
                withUiContext {
                    Notification(
                        "Aspire",
                        AspireBundle.message("notification.new.version.is.available"),
                        "",
                        NotificationType.INFORMATION
                    )
                        .addAction(object :
                            NotificationAction(AspireBundle.message("notifications.go.to.documentation")) {
                            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                                BrowserUtil.open("https://learn.microsoft.com/en-us/dotnet/aspire/whats-new/preview-7")
                            }
                        })
                        .addAction(object :
                            NotificationAction(AspireBundle.message("notifications.do.not.check.for.updates")) {
                            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                                ShowSettingsUtil.getInstance().editConfigurable(e.project, AspireConfigurable())
                            }
                        })
                        .notify(project)
                }
            }
        }
    }

    private fun getDotnetPath(): String? {
        val cliExePath = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value?.cliExePath
        LOG.trace("dotnet cli path: $cliExePath")
        return cliExePath
    }

    private fun getListOfWorkloads(dotnetPath: String): ProcessOutput? {
        val commandLine = GeneralCommandLine()
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withExePath(dotnetPath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters("workload", "list")

        try {
            return ExecUtil.execAndGetOutput(commandLine)
        } catch (_: Exception) {
            LOG.warn("Unable to get workload list")
            return null
        }
    }

    private fun isAspireWorkloadInstalled(dotnetPath: String): Boolean {
        val output = getListOfWorkloads(dotnetPath) ?: return false

        return if (output.checkSuccess(LOG)) {
            LOG.trace("List of workloads: ${output.stdout}")
            aspireRegex.containsMatchIn(output.stdout)
        } else {
            false
        }
    }

    private fun isCurrentVersionInstalled(dotnetPath: String): Boolean {
        val currentVersion = getWorkloadVersion(dotnetPath) ?: return false
        return currentVersion >= aspireActualVersion
    }

    private fun getWorkloadVersion(dotnetPath: String): WorkloadVersion? {
        val output = getListOfWorkloads(dotnetPath) ?: return null

        return if (output.checkSuccess(LOG)) {
            LOG.trace("List of workloads: ${output.stdout}")
            val versionString = aspireVersionRegex.find(output.stdout)?.groups?.get(1)?.value ?: return null
            return WorkloadVersion(versionString)
        } else {
            null
        }
    }
}