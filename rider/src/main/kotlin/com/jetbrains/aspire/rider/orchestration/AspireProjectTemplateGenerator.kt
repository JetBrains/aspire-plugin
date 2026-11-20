package com.jetbrains.aspire.rider.orchestration

import com.intellij.diagnostic.rethrowControlFlowException
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
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseNotNullOnce
import com.jetbrains.rd.util.threading.coroutines.createTerminatedAfter
import com.jetbrains.rider.model.ProjectTemplatesModel
import com.jetbrains.rider.model.RdProjectTemplate
import com.jetbrains.rider.model.RdProjectTemplateChoiceOption
import com.jetbrains.rider.model.RdProjectTemplateSession
import com.jetbrains.rider.model.projectTemplatesModel
import com.jetbrains.rider.projectView.projectTemplates.providers.RiderProjectTemplateProvider
import com.jetbrains.rider.projectView.projectTemplates.utils.ProjectTemplatesExpanderUtils
import com.jetbrains.rider.projectView.solutionDirectory
import com.jetbrains.rider.projectView.solutionName
import com.jetbrains.rider.protocol.protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
internal class AspireProjectTemplateGenerator(private val project: Project) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireProjectTemplateGenerator = project.service()
        private val LOG = logger<AspireProjectTemplateGenerator>()

        private const val APP_HOST_TEMPLATE_ID = "Aspire.AppHost.CSharp"
        private const val SERVICE_DEFAULTS_TEMPLATE_ID = "Aspire.ServiceDefaults.CSharp"
        private const val MAUI_SERVICE_DEFAULTS_TEMPLATE_ID = "MauiAspire.ServiceDefaults.CSharp"
        private const val APP_HOST_PROJECT_DEFAULT_SUFFIX = "AppHost"
        private const val SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX = "ServiceDefaults"
        private const val MAUI_SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX = "Maui.ServiceDefaults"
        private const val FRAMEWORK_PARAMETER_NAME = "Framework"
    }

    /**
     * Generates a single Aspire project from a template.
     *
     * @param templateId The template ID to use for generation (e.g., "Aspire.AppHost.CSharp")
     * @param projectSuffix The suffix to append to the solution name (e.g., "AppHost")
     * @return The path to the generated project file, or null if generation failed
     */
    suspend fun generateProjectFromTemplate(
        templateId: String,
        projectSuffix: String
    ): Path? = generateProjectFromTemplate(templateId, projectSuffix, emptyList(), null)

    /**
     * Generates an AppHost project using the predefined AppHost template.
     *
     * @return The path to the generated AppHost project file, or null if generation failed
     */
    suspend fun generateAppHost(projectPaths: List<Path>): Path? =
        generateProjectFromTemplate(
            APP_HOST_TEMPLATE_ID,
            APP_HOST_PROJECT_DEFAULT_SUFFIX,
            projectPaths,
            TemplateFrameworkSelectionStrategy.Highest
        )

    /**
     * Generates a ServiceDefaults project using the predefined ServiceDefaults template.
     *
     * @return The path to the generated ServiceDefaults project file, or null if generation failed
     */
    suspend fun generateServiceDefaults(projectPaths: List<Path>): Path? =
        generateProjectFromTemplate(
            SERVICE_DEFAULTS_TEMPLATE_ID,
            SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX,
            projectPaths,
            TemplateFrameworkSelectionStrategy.Lowest
        )

    /**
     * Generates a Maui ServiceDefaults project using the predefined Maui ServiceDefaults template.
     *
     * @return The path to the generated Maui ServiceDefaults project file, or null if generation failed
     */
    suspend fun generateMauiServiceDefaults(projectPaths: List<Path>): Path? =
        generateProjectFromTemplate(
            MAUI_SERVICE_DEFAULTS_TEMPLATE_ID,
            MAUI_SERVICE_DEFAULTS_PROJECT_DEFAULT_SUFFIX,
            projectPaths,
            TemplateFrameworkSelectionStrategy.Lowest
        )

    private suspend fun generateProjectFromTemplate(
        templateId: String,
        projectSuffix: String,
        projectPaths: List<Path>,
        frameworkSelectionStrategy: TemplateFrameworkSelectionStrategy?
    ): Path? = withBackgroundProgress(project, AspireRiderBundle.message("progress.generating.aspire.projects")) {
        LOG.info("Generating Aspire project for template: $templateId")

        val model = project.protocol.projectTemplatesModel
        val session = RiderProjectTemplateProvider.createSession(createSolution = false)

        serviceLifetime.usingNested { lifetime ->
            val template = findTemplateById(model, session, templateId, lifetime)
                ?: return@withBackgroundProgress null

            val templateParameters = if (frameworkSelectionStrategy == null) {
                emptyMap()
            } else {
                selectFrameworkParameter(template, projectPaths, frameworkSelectionStrategy)
            }

            return@withBackgroundProgress generateProjectFromTemplate(
                session,
                template,
                projectSuffix,
                templateParameters
            )
        }
    }

    private suspend fun selectFrameworkParameter(
        template: RdProjectTemplate,
        projectPaths: List<Path>,
        strategy: TemplateFrameworkSelectionStrategy
    ): Map<String, String> {
        val frameworkOption = template.options
            .filterIsInstance<RdProjectTemplateChoiceOption>()
            .firstOrNull { it.name == FRAMEWORK_PARAMETER_NAME }
        if (frameworkOption == null) {
            LOG.info("Template ${template.id} has no $FRAMEWORK_PARAMETER_NAME choice; using its default")
            return emptyMap()
        }

        if (frameworkOption.choices.isEmpty()) {
            LOG.info("Template ${template.id} has no choices for $FRAMEWORK_PARAMETER_NAME; using its default")
            return emptyMap()
        }

        val projectTargetFrameworks = try {
            getProjectsTargetFrameworks(project, projectPaths)?.projects?.flatMap { it.targetFrameworks }
                ?: run {
                    LOG.info("Unable to get target frameworks for template ${template.id}; using its default")
                    return emptyMap()
                }
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.info("Unable to get target frameworks for template ${template.id}; using its default", e)
            return emptyMap()
        }

        if (projectTargetFrameworks.isEmpty()) {
            LOG.info("No target frameworks found for template ${template.id}; using its default")
            return emptyMap()
        }

        val supportedFrameworks = frameworkOption.choices.mapTo(mutableSetOf()) { it.name }
        val selectedFramework = selectTemplateFramework(projectTargetFrameworks, supportedFrameworks, strategy)
        if (selectedFramework == null) {
            LOG.info("No matching $FRAMEWORK_PARAMETER_NAME choice for template ${template.id}; using its default")
            return emptyMap()
        }

        return mapOf(FRAMEWORK_PARAMETER_NAME to selectedFramework)
    }

    private suspend fun findTemplateById(
        model: ProjectTemplatesModel,
        session: RdProjectTemplateSession,
        templateId: String,
        lifetime: Lifetime
    ): RdProjectTemplate? {
        val deferredTemplate = CompletableDeferred<RdProjectTemplate?>()

        val advisingLifetime = lifetime.createTerminatedAfter(Duration.ofSeconds(30), Dispatchers.Default)

        advisingLifetime.onTermination {
            application.invokeLater {
                model.session.set(null)
            }

            if (!deferredTemplate.isCompleted) {
                deferredTemplate.complete(null)
            }
        }

        session.templatesRaw.adviseNotNullOnce(advisingLifetime) { templates ->
            val template = templates.filter { it.id.startsWith(templateId) }.maxByOrNull { it.id }
            deferredTemplate.complete(template)
        }

        withContext(Dispatchers.EDT) {
            model.session.set(session)
        }

        val result = deferredTemplate.await()

        if (result == null) {
            LOG.warn("Unable to find template: $templateId")
            notifyAboutMissingTemplates()
            return null
        }

        return result
    }

    private suspend fun generateProjectFromTemplate(
        session: RdProjectTemplateSession,
        template: RdProjectTemplate?,
        projectNameSuffix: String,
        templateParameters: Map<String, String>
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
            projectDirectoryPath,
            templateParameters
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
        templateParameters: Map<String, String>,
    ) = ProjectTemplatesExpanderUtils.expandProjectTemplate(
        session,
        project,
        template.id,
        template.sdk,
        projectName,
        projectDirectory.toFile(),
        project.solutionDirectory,
        templateParameters,
        false
    )

    private suspend fun notifyAboutMissingTemplates() = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.unable.to.find.aspire.templates"),
            "",
            NotificationType.WARNING
        )
            .addAction(object :
                NotificationAction(AspireRiderBundle.message("notification.how.to.install.aspire.templates")) {
                override fun actionPerformed(e: AnActionEvent, n: Notification) {
                    BrowserUtil.browse("https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/aspire-sdk-templates?pivots=dotnet-cli#install-the-net-aspire-templates")
                }
            })
            .notify(project)
    }
}