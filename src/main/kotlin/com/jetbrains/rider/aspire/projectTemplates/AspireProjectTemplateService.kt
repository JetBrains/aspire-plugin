package com.jetbrains.rider.aspire.projectTemplates

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.model.RdProjectTemplate
import com.jetbrains.rider.model.projectTemplatesModel
import com.jetbrains.rider.projectView.projectTemplates.providers.RiderProjectTemplateProvider
import com.jetbrains.rider.protocol.protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class AspireProjectTemplateService(
    private val project: Project,
    private val scope: CoroutineScope
) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireProjectTemplateService = project.service()
        private val LOG = logger<AspireProjectTemplateService>()

        private const val APP_HOST_TEMPLATE_ID = "Aspire.AppHost.CSharp"
        private const val SERVICE_DEFAULTS_TEMPLATE_ID = "Aspire.ServiceDefaults.CSharp"
    }

    suspend fun createHotProjectFromTemplate() {
        LOG.info("Generate Aspire projects for the solution")

        val (appHostTemplate, serviceDefaultsTemplate) = loadAspireTemplates()

        if (appHostTemplate == null || serviceDefaultsTemplate == null) {
            LOG.warn("Unable to load Aspire project templates")
            return
        }


    }

    private suspend fun loadAspireTemplates(): Pair<RdProjectTemplate?, RdProjectTemplate?> =
        withBackgroundProgress(project, AspireBundle.message("progress.load.aspire.templates")) {
            val model = project.protocol.projectTemplatesModel
            val session = RiderProjectTemplateProvider.createSession(createSolution = false, useCachedTemplates = true)

            serviceLifetime.usingNested { lifetime ->
                lifetime.onTermination {
                    application.invokeLater {
                        model.session.set(null)
                    }
                }

                val appHostDeferredTemplate = CompletableDeferred<RdProjectTemplate?>()
                val serviceDefaultsDeferredTemplate = CompletableDeferred<RdProjectTemplate?>()

                session.templatesRaw.advise(lifetime) { templates ->
                    if (templates != null) {
                        val appHostTemplate = templates.firstOrNull { it.id.contains(APP_HOST_TEMPLATE_ID) }
                        appHostDeferredTemplate.complete(appHostTemplate)
                        val serviceDefaultsTemplate =
                            templates.firstOrNull { it.id.contains(SERVICE_DEFAULTS_TEMPLATE_ID) }
                        serviceDefaultsDeferredTemplate.complete(serviceDefaultsTemplate)
                    }
                }

                withContext(Dispatchers.EDT) {
                    model.session.set(session)
                }

                val appHostResult = appHostDeferredTemplate.await()
                val serviceDefaultsResult = serviceDefaultsDeferredTemplate.await()

                return@withBackgroundProgress appHostResult to serviceDefaultsResult
            }
        }
}