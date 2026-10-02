@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.services

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
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireAppHostModel
import com.jetbrains.aspire.worker.AspireAppHostStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

internal class AspireAppHostViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    val appHost: AspireAppHostModel
) : ServiceViewProvidingContributor<AspireResourceViewModel, AspireAppHostViewModel>, Disposable {
    companion object {
        private val LOG = logger<AspireAppHostViewModel>()

        @ApiStatus.Internal
        fun createUiState(data: AspireAppHostData, consoleComponent: JComponent): AppHostUiState =
            when (data.status) {
                AspireAppHostStatus.Inactive -> AppHostUiState.Initial

                AspireAppHostStatus.Starting,
                AspireAppHostStatus.Started -> AppHostUiState.Active(data.dashboardUrl, consoleComponent)

                AspireAppHostStatus.Stopped -> AppHostUiState.Inactive(consoleComponent)
            }
    }

    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    val appHostId: AspireAppHostId = appHost.appHostId
    val displayName: String = appHost.data.value.name

    private val logProcessHandler = LogProcessHandler()
    private val logConsole = TerminalExecutionConsoleBuilder(project)
        .convertLfToCrlfForProcessWithoutPty(true)
        .build()
        .apply { attachToProcess(logProcessHandler) }
        .also { Disposer.register(this, it) }

    val uiState: StateFlow<AppHostUiState> = appHost.data
        .map { data -> createUiState(data, logConsole.component) }
        .stateIn(cs, SharingStarted.Eagerly, AppHostUiState.Initial)

    private val resourceViewModels: StateFlow<List<AspireResourceViewModel>> =
        appHost.rootResources
            .toResourceViewModels(project, cs, this)

    init {
        logProcessHandler.startNotify()

        cs.launch {
            appHost.logFlow.collectLatest { logFlow ->
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

    private suspend fun selectAppHost() {
        withContext(Dispatchers.Main) {
            ServiceViewManager
                .getInstance(project)
                .select(this, AspireMainServiceViewContributor::class.java, true, true)
        }
    }

    private suspend fun expand() {
        withContext(Dispatchers.Main) {
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
        LOG.trace { "Disposing AspireAppHost VM for project: ${appHostId.value}" }
        cs.cancel()
    }
}
