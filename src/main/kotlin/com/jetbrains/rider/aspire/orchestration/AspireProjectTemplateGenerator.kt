package com.jetbrains.rider.aspire.orchestration

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.util.text.UniqueNameGenerator
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseNotNullOnce
import com.jetbrains.rd.util.threading.coroutines.createTerminatedAfter
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.model.ProjectTemplatesModel
import com.jetbrains.rider.model.RdProjectTemplate
import com.jetbrains.rider.model.RdProjectTemplateSession
import com.jetbrains.rider.model.projectTemplatesModel
import com.jetbrains.rider.projectView.projectTemplates.providers.RiderProjectTemplateProvider
import com.jetbrains.rider.projectView.projectTemplates.utils.ProjectTemplatesExpanderUtils
import com.jetbrains.rider.projectView.solutionDirectory
import com.jetbrains.rider.projectView.solutionName
import com.jetbrains.rider.protocol.protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class AspireProjectTemplateGenerator(private val project: Project) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireProjectTemplateGenerator = project.service()
        private val LOG = logger<AspireProjectTemplateGenerator>()

        private const val APP_HOST_TEMPLATE_ID = "Aspire.AppHost.CSharp"
        private const val SERVICE_DEFAULTS_TEMPLATE_ID = "Aspire.ServiceDefaults.CSharp"
        private const val APP_HOST_PROJECT_DEFAULT_SUFFIX = "AppHost"
        private const val SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX = "ServiceDefaults"
    }

    /**
     * Generates Aspire projects using predefined templates for the current solution.
     * The method could generate AppHost and ServiceDefaults types of projects.
     *
     * @param generateAppHost defines whether to generate the App Host project.
     * @param generateServiceDefaults defines whether to generate the Service Defaults project.
     * @return paths of the generated project files.
     */
    suspend fun generateAspireProjectsFromTemplates(
        generateAppHost: Boolean,
        generateServiceDefaults: Boolean
    ) = withBackgroundProgress(project, AspireBundle.message("progress.generating.aspire.projects")) {
        LOG.info("Generating Aspire projects for the solution")

        val model = project.protocol.projectTemplatesModel
        val session = RiderProjectTemplateProvider.createSession(createSolution = false, useCachedTemplates = true)

        serviceLifetime.usingNested { lifetime ->
            val (appHostTemplate, serviceDefaultsTemplate) = findAspireTemplates(model, session, lifetime)
                ?: return@withBackgroundProgress null

            val appHostProjectPath = if (generateAppHost) async {
                generateProjectFromTemplate(
                    session,
                    appHostTemplate,
                    APP_HOST_PROJECT_DEFAULT_SUFFIX
                )
            } else null

            val serviceDefaultsProjectPath = if (generateServiceDefaults) async {
                generateProjectFromTemplate(
                    session,
                    serviceDefaultsTemplate,
                    SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX
                )
            } else null

            return@withBackgroundProgress appHostProjectPath?.await() to serviceDefaultsProjectPath?.await()
        }
    }

    private suspend fun findAspireTemplates(
        model: ProjectTemplatesModel,
        session: RdProjectTemplateSession,
        lifetime: Lifetime
    ): Pair<RdProjectTemplate?, RdProjectTemplate?>? {
        val appHostDeferredTemplate = CompletableDeferred<RdProjectTemplate?>()
        val serviceDefaultsDeferredTemplate = CompletableDeferred<RdProjectTemplate?>()

        val advisingLifetime = lifetime.createTerminatedAfter(Duration.ofSeconds(30), Dispatchers.Default)

        advisingLifetime.onTermination {
            application.invokeLater {
                model.session.set(null)
            }

            if (!appHostDeferredTemplate.isCompleted) {
                appHostDeferredTemplate.complete(null)
            }
            if (!serviceDefaultsDeferredTemplate.isCompleted) {
                serviceDefaultsDeferredTemplate.complete(null)
            }
        }

        session.templatesRaw.adviseNotNullOnce(advisingLifetime) { templates ->
            val appHostTemplate =
                templates.firstOrNull { it.id.contains(APP_HOST_TEMPLATE_ID) }
            appHostDeferredTemplate.complete(appHostTemplate)

            val serviceDefaultsTemplate =
                templates.firstOrNull { it.id.contains(SERVICE_DEFAULTS_TEMPLATE_ID) }
            serviceDefaultsDeferredTemplate.complete(serviceDefaultsTemplate)
        }

        withContext(Dispatchers.EDT) {
            model.session.set(session)
        }

        val appHostResult = appHostDeferredTemplate.await()
        val serviceDefaultsResult = serviceDefaultsDeferredTemplate.await()

        if (appHostResult == null || serviceDefaultsResult == null) {
            notifyAboutMissingTemplates()
            return null
        }

        return appHostResult to serviceDefaultsResult
    }

    private suspend fun generateProjectFromTemplate(
        session: RdProjectTemplateSession,
        template: RdProjectTemplate?,
        projectNameSuffix: String
    ): Path? {
        if (template == null) {
            LOG.warn("Unable to find Aspire template for the $projectNameSuffix project")
            return null
        }

        val solutionName = project.solutionName
        val defaultProjectName = "$solutionName.$projectNameSuffix"
        val solutionDirectoryPath = project.solutionDirectory.toPath()

        val projectName = UniqueNameGenerator.generateUniqueName(defaultProjectName) { name ->
            !solutionDirectoryPath.resolve(name).exists()
        }
        val projectDirectoryPath = solutionDirectoryPath.resolve(projectName)

        val result = generateProjectFromTemplate(
            session,
            template,
            projectName,
            projectDirectoryPath
        )

        if (result.errorMessage != null) {
            LOG.warn("Unable to generate project from template: ${result.errorMessage}")
            return null
        }

        return result.info?.projectFiles?.firstOrNull()?.toNioPathOrNull()
    }

    private suspend fun generateProjectFromTemplate(
        session: RdProjectTemplateSession,
        template: RdProjectTemplate,
        projectName: String,
        projectDirectory: Path,
    ) = ProjectTemplatesExpanderUtils.expandProjectTemplate(
        session,
        project,
        template.id,
        template.sdk,
        projectName,
        projectDirectory.toFile(),
        project.solutionDirectory,
        mapOf(),
        false
    )

    private suspend fun notifyAboutMissingTemplates() = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireBundle.message("notification.unable.to.find.aspire.templates"),
            "",
            NotificationType.WARNING
        )
            .addAction(object :
                NotificationAction(AspireBundle.message("notification.how.to.install.aspire.templates")) {
                override fun actionPerformed(e: AnActionEvent, n: Notification) {
                    BrowserUtil.browse("https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/aspire-sdk-templates?pivots=dotnet-cli#install-the-net-aspire-templates")
                }
            })
            .notify(project)
    }
}