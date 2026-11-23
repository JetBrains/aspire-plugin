package com.jetbrains.aspire.rider.orchestration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.text.SemVer
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rider.model.RdNuGetFail
import com.jetbrains.rider.model.RdNuGetInstalledPackage
import com.jetbrains.rider.model.RdNuGetPackageInfo
import com.jetbrains.rider.model.RdNuGetSuccess
import com.jetbrains.rider.model.RdProjectTemplateSession
import com.jetbrains.rider.projectView.projectTemplates.TemplatesInstallService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class AspireTemplatePackageManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireTemplatePackageManager = project.service()

        private val LOG = logger<AspireTemplatePackageManager>()
        private val PACKAGE_CHECK_TIMEOUT = 15.seconds
        private val SUPPORTED_VERSION = requireNotNull(SemVer.parseFromText(ASPIRE_PROJECT_TEMPLATES_PACKAGE_VERSION))
        private val VERSION_PATTERN =
            Regex("""\d+\.\d+\.\d+(?:-[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*)?(?:\+[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*)?""")
    }

    suspend fun notifyIfOutdated() {
        val installedVersion = try {
            withTimeoutOrNull(PACKAGE_CHECK_TIMEOUT) {
                val sessions = AspireProjectTemplateSessionService.getInstance(project)
                sessions.withSession(useCachedTemplates = true) { session ->
                    withContext(Dispatchers.EDT) { getInstalledVersion(session) }
                }
            }
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.debug("Unable to check the installed Aspire template version", e)
            null
        } ?: return

        if (installedVersion >= SUPPORTED_VERSION) return

        withContext(Dispatchers.EDT) {
            Notification(
                "Aspire",
                AspireRiderBundle.message("notification.aspire.templates.outdated.title"),
                AspireRiderBundle.message(
                    "notification.aspire.templates.outdated.description",
                    installedVersion.rawVersion,
                    ASPIRE_PROJECT_TEMPLATES_PACKAGE_VERSION,
                ),
                NotificationType.WARNING,
            ).addAction(object : NotificationAction(AspireRiderBundle.message("notification.aspire.templates.update.action")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    e.coroutineScope.launch(Dispatchers.Default) { updateTemplates(notification) }
                }
            }).notify(project)
        }
    }

    private suspend fun updateTemplates(warning: Notification) {
        val result = withBackgroundProgress(project, AspireRiderBundle.message("progress.updating.aspire.templates")) {
            try {
                val sessions = AspireProjectTemplateSessionService.getInstance(project)
                sessions.withSession(useCachedTemplates = true) { session ->
                    installSupportedVersion(session)
                } ?: UpdateResult.Failed
            } catch (e: Exception) {
                rethrowControlFlowException(e)
                LOG.warn("Unable to update Aspire templates", e)
                UpdateResult.Failed
            }
        }

        withContext(Dispatchers.EDT) {
            when (result) {
                UpdateResult.Updated -> {
                    warning.expire()
                    Notification(
                        "Aspire",
                        AspireRiderBundle.message("notification.aspire.templates.update.success", ASPIRE_PROJECT_TEMPLATES_PACKAGE_VERSION),
                        "",
                        NotificationType.INFORMATION,
                    ).notify(project)
                }
                UpdateResult.AlreadyCurrent -> warning.expire()
                UpdateResult.Failed -> Notification(
                    "Aspire",
                    AspireRiderBundle.message("notification.aspire.templates.update.failure.title"),
                    AspireRiderBundle.message("notification.aspire.templates.update.failure.description"),
                    NotificationType.ERROR,
                ).notify(project)
            }
        }
    }

    private suspend fun installSupportedVersion(session: RdProjectTemplateSession): UpdateResult = withContext(Dispatchers.EDT) {
        val installService = TemplatesInstallService.getInstance()
        if (installService.currentlyInstalling.get() != null) return@withContext UpdateResult.Failed

        val packageInfo = RdNuGetPackageInfo(ASPIRE_PROJECT_TEMPLATES_PACKAGE_NAME, ASPIRE_PROJECT_TEMPLATES_PACKAGE_VERSION)
        installService.startInstallation("${packageInfo.id}:${packageInfo.version}")
        try {
            val installedVersion = withTimeoutOrNull(PACKAGE_CHECK_TIMEOUT) { getInstalledVersion(session) }
                ?: return@withContext UpdateResult.Failed
            if (installedVersion >= SUPPORTED_VERSION)
                return@withContext UpdateResult.AlreadyCurrent

            val result = session.installPackage.startSuspending(packageInfo)
            if (result is RdNuGetSuccess)
                return@withContext UpdateResult.Updated
            if (result is RdNuGetFail)
                LOG.warn("Unable to update Aspire templates: ${result.message}")
            UpdateResult.Failed
        } finally {
            installService.finishInstallation()
        }
    }

    private suspend fun getInstalledVersion(session: RdProjectTemplateSession): SemVer? {
        val result = session.getInstalledPackage.startSuspending(ASPIRE_PROJECT_TEMPLATES_PACKAGE_NAME)
        if (result is RdNuGetFail) {
            LOG.debug("Unable to check the installed Aspire template version: ${result.message}")
            return null
        }

        val packageInfo = ((result as? RdNuGetSuccess)?.response as? RdNuGetInstalledPackage)?.`package` ?: return null
        if (!packageInfo.id.equals(ASPIRE_PROJECT_TEMPLATES_PACKAGE_NAME, ignoreCase = true)) return null

        return packageInfo.version.takeIf(VERSION_PATTERN::matches)?.let(SemVer::parseFromText)
    }

    private enum class UpdateResult { Updated, AlreadyCurrent, Failed }
}
