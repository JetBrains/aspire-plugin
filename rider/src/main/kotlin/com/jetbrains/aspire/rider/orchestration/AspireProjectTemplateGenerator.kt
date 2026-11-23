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
import com.intellij.util.text.UniqueNameGenerator
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseNotNullOnce
import com.jetbrains.rd.util.threading.coroutines.createTerminatedAfter
import com.jetbrains.rider.model.RdProjectTemplate
import com.jetbrains.rider.model.RdProjectTemplateChoiceOption
import com.jetbrains.rider.model.RdProjectTemplateSession
import com.jetbrains.rider.projectView.projectTemplates.utils.ProjectTemplatesExpanderUtils
import com.jetbrains.rider.projectView.solutionDirectory
import com.jetbrains.rider.projectView.solutionName
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

        val sessions = AspireProjectTemplateSessionService.getInstance(project)
        sessions.withSession(useCachedTemplates = false) { session ->
            val templates = serviceLifetime.usingNested { lifetime ->
                findTemplatesById(session, templateId, lifetime)
            }
            if (templates.isEmpty()) return@withSession null

            val (template, templateParameters) = selectTemplateAndParameters(
                templateId,
                templates,
                projectPaths,
                frameworkSelectionStrategy
            )

            generateProjectFromTemplate(
                session,
                template,
                projectSuffix,
                templateParameters
            )
        }
    }

    /**
     * Selects one of the [templates] found by [templateId] and the template parameters for it.
     *
     * Several templates can match the same ID, for example, ServiceDefaults templates from MAUI template packs
     * for different .NET versions. Each of them may support only some frameworks,
     * so the template is selected together with the `Framework` choice.
     * If the framework can't be selected, the latest template with its default parameters is used.
     */
    private suspend fun selectTemplateAndParameters(
        templateId: String,
        templates: List<RdProjectTemplate>,
        projectPaths: List<Path>,
        strategy: TemplateFrameworkSelectionStrategy?
    ): Pair<RdProjectTemplate, Map<String, String>> {
        val templatesByCandidate = templates.associateBy { template ->
            TemplateCandidate(
                template.id,
                parseTemplateIdVersion(template.id, templateId),
                getFrameworkChoices(template)
            )
        }
        val candidates = templatesByCandidate.keys.toList()
        val latestCandidate = requireNotNull(selectLatestTemplate(candidates))
        val latestTemplate = templatesByCandidate.getValue(latestCandidate)

        if (strategy == null) return latestTemplate to emptyMap()

        if (candidates.all { it.frameworks.isEmpty() }) {
            LOG.info("Template $templateId has no $FRAMEWORK_PARAMETER_NAME choices; using its default")
            return latestTemplate to emptyMap()
        }

        val projectTargetFrameworks = getProjectTargetFrameworks(projectPaths)
        if (projectTargetFrameworks == null) {
            LOG.info("No target frameworks for template $templateId; using its default")
            return latestTemplate to emptyMap()
        }

        val selection = selectTemplate(candidates, projectTargetFrameworks, strategy)
        if (selection == null) {
            LOG.info("No matching $FRAMEWORK_PARAMETER_NAME choice for template $templateId; using its default")
            return latestTemplate to emptyMap()
        }

        val selectedTemplate = templatesByCandidate.entries.first { it.key.id == selection.id }.value
        LOG.info("Selected template ${selectedTemplate.id} with $FRAMEWORK_PARAMETER_NAME ${selection.framework}")

        return selectedTemplate to mapOf(FRAMEWORK_PARAMETER_NAME to selection.framework)
    }

    private fun getFrameworkChoices(template: RdProjectTemplate): Set<String> =
        template.options
            .filterIsInstance<RdProjectTemplateChoiceOption>()
            .firstOrNull { it.name == FRAMEWORK_PARAMETER_NAME }
            ?.choices
            ?.mapTo(mutableSetOf()) { it.name }
            .orEmpty()

    /**
     * Returns the target frameworks of [projectPaths], or `null` if they can't be obtained or are empty.
     */
    private suspend fun getProjectTargetFrameworks(projectPaths: List<Path>): List<String>? {
        val projectTargetFrameworks = try {
            getProjectsTargetFrameworks(project, projectPaths)?.projects?.flatMap { it.targetFrameworks }
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.info("Unable to get target frameworks for projects $projectPaths", e)
            return null
        }

        if (projectTargetFrameworks == null) {
            LOG.info("Unable to get target frameworks for projects $projectPaths")
            return null
        }

        if (projectTargetFrameworks.isEmpty()) {
            LOG.info("No target frameworks found for projects $projectPaths")
            return null
        }

        return projectTargetFrameworks
    }

    private suspend fun findTemplatesById(
        session: RdProjectTemplateSession,
        templateId: String,
        lifetime: Lifetime
    ): List<RdProjectTemplate> {
        val deferredTemplates = CompletableDeferred<List<RdProjectTemplate>>()

        val advisingLifetime = lifetime.createTerminatedAfter(Duration.ofSeconds(30), Dispatchers.Default)

        advisingLifetime.onTermination {
            deferredTemplates.complete(emptyList())
        }

        withContext(Dispatchers.EDT) {
            session.templatesRaw.adviseNotNullOnce(advisingLifetime) { templates ->
                deferredTemplates.complete(templates.filter { it.id.startsWith(templateId) })
            }
        }

        val result = deferredTemplates.await()

        if (result.isEmpty()) {
            LOG.warn("Unable to find template: $templateId")
            notifyAboutMissingTemplates()
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
