@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.intellij.util.application
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireAppHostViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    appHost: AspireAppHost
) : ServiceViewProvidingContributor<AspireResourceViewModel, AspireAppHostViewModel>, Disposable {
    companion object {
        private val LOG = logger<AspireAppHostViewModel>()
    }

    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    val appHostMainFilePath = appHost.mainFilePath
    val displayName: String = appHost.name

    private val logProcessHandler = LogProcessHandler()
    private val logConsole = TerminalExecutionConsoleBuilder(project)
        .convertLfToCrlfForProcessWithoutPty(true)
        .build()
        .apply { attachToProcess(logProcessHandler) }
        .also { Disposer.register(this, it) }

    val uiState: StateFlow<AppHostUiState> = combine(
        appHost.appHostState,
        appHost.dashboardUrl
    ) { state, url ->
        when (state) {
            is AspireAppHost.AspireAppHostState.Started ->
                AppHostUiState.Active(url, logConsole.component)

            else ->
                AppHostUiState.Inactive(url)
        }
    }.stateIn(cs, SharingStarted.Eagerly, AppHostUiState.Inactive(null))

    private val resourceViewModels: StateFlow<List<AspireResourceViewModel>> =
        appHost.rootResources
            .runningFold(emptyList<AspireResourceViewModel>()) { currentViewModels, newResources ->
                val currentViewModelsByName = currentViewModels.associateBy { it.resource.resourceName }
                val newIds = newResources.map { it.resourceName }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.resourceName in newIds) {
                            LOG.trace { "Resource ViewModel for ${viewModel.resourceName} already exists" }
                            add(viewModel)
                        } else {
                            LOG.trace { "Disposing Resource ViewModel for ${viewModel.resourceName}" }
                            Disposer.dispose(viewModel)
                        }
                    }

                    for (newResource in newResources) {
                        if (newResource.resourceName !in currentViewModelsByName) {
                            LOG.trace { "Creating new Resource ViewModel for ${newResource.resourceName}" }
                            val resourceVM = AspireResourceViewModel(project, cs, newResource)
                            Disposer.register(this@AspireAppHostViewModel, resourceVM)
                            add(resourceVM)
                        }
                    }
                }.sortedWith(
                    compareBy(
                        { it.resource.resourceState.value.type },
                        { it.resource.resourceState.value.name })
                )
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    init {
        logProcessHandler.startNotify()

        cs.launch {
            appHost.currentLogFlow.collectLatest { logFlow ->
                if (logFlow == null) return@collectLatest

                logConsole.clear()
                logFlow.collect { entry ->
                    val outputType = if (entry.isStdErr) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
                    logProcessHandler.notifyTextAvailable(entry.text, outputType)
                }
            }
        }

        cs.launch {
            var wasActive = false
            uiState.collect { state ->
                val isActive = state is AppHostUiState.Active
                if (isActive && !wasActive) selectAppHost()
                wasActive = isActive
                sendServiceChangedEvent()
            }
        }

        cs.launch {
            resourceViewModels
                .drop(1)
                .collect {
                    sendServiceChildrenChangedEvent()
                    expand()
                }
        }
    }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireAppHostViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        resourceViewModel: AspireResourceViewModel
    ) = resourceViewModel.getViewDescriptor(project)

    override fun getServices(project: Project) = resourceViewModels.value

    private fun selectAppHost() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .select(this, AspireMainServiceViewContributor::class.java, true, true)
        }
    }

    private fun expand() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .expand(this, AspireMainServiceViewContributor::class.java)
        }
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceChildrenChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
        LOG.trace { "Disposing AspireAppHost VM for project: $appHostMainFilePath" }
        cs.cancel()
    }
}