@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.nameWithoutExtension

@ApiStatus.Internal
class AspireAppHostViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    private val appHost: AspireAppHost
) : ServiceViewProvidingContributor<AspireResource, AspireAppHostViewModel>, Disposable {
    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    val appHostMainFilePath = appHost.mainFilePath
    val displayName: String = appHostMainFilePath.nameWithoutExtension

    private val resourceViewModels: StateFlow<List<AspireResource>> =
        appHost.resources
            .map { resources ->
                resources
                    .filter { it.parentResourceName == null }
                    .sortedWith(compareBy({ it.data.type }, { it.data.name }))
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<AppHostUiState> = combine(
        appHost.appHostState,
        appHost.dashboardUrl
    ) { state, url ->
        when (state) {
            is AspireAppHost.AspireAppHostState.Started ->
                AppHostUiState.Active(url, state.console)
            else ->
                AppHostUiState.Inactive(url)
        }
    }.stateIn(cs, SharingStarted.Eagerly, AppHostUiState.Inactive(null))

    init {
        cs.launch {
            var previousConsole: ConsoleView? = null

            uiState.collect { state ->
                val newConsole = (state as? AppHostUiState.Active)?.consoleView
                if (previousConsole != null && previousConsole != newConsole) {
                    Disposer.dispose(requireNotNull(previousConsole))
                }

                if (state is AppHostUiState.Active && previousConsole == null) {
                    selectAppHost()
                }
                previousConsole = newConsole

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

        cs.launch {
            appHost
                .resourcesReloadSignal
                .collect {
                    sendServiceChildrenChangedEvent()
                }
        }
    }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireAppHostViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        resourceViewModel: AspireResource
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
    }
}