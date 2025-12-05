@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.process.ProcessHandler
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
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.io.path.nameWithoutExtension

class AspireAppHostViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    private val appHost: AspireAppHost
) : ServiceViewProvidingContributor<AspireResourceViewModel, AspireAppHostViewModel>, Disposable {
    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    val appHostMainFilePath = appHost.mainFilePath
    val displayName: String = appHostMainFilePath.nameWithoutExtension

    private val resourceViewModels: StateFlow<List<AspireResourceViewModel>> =
        appHost.resources
            .runningFold(emptyList<AspireResourceViewModel>()) { currentViewModels, newResources ->
                val currentResourceIds = currentViewModels.associateBy { it.resourceId }
                val newResourceIds = newResources.map { it.resourceId }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.resourceId in newResourceIds) {
                            add(viewModel)
                        }
                    }

                    for (newResource in newResources) {
                        if (newResource.resourceId !in currentResourceIds) {
                            val resourceVM = AspireResourceViewModel(project, cs, newResource)
                            Disposer.register(this@AspireAppHostViewModel, resourceVM)
                            add(resourceVM)
                        }
                    }
                }
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    var isActive: Boolean = false
        private set
    var dashboardUrl: String? = null
        private set
    var consoleView: ConsoleView? = null
        private set

    init {
        cs.launch {
            appHost.dashboardUrl.collect {
                dashboardUrl = it
                sendServiceChangedEvent()
            }
        }

        cs.launch {
            appHost.appHostState.collect {
                when (it) {
                    is AspireAppHost.AspireAppHostState.Inactive -> {
                        isActive = false
                    }

                    is AspireAppHost.AspireAppHostState.Started -> {
                        isActive = true
                        updateConsoleView(it.processHandler)
                        selectAppHost()
                    }

                    is AspireAppHost.AspireAppHostState.Stopped -> {
                        isActive = false
                    }
                }

                sendServiceChangedEvent()
            }
        }

        cs.launch {
            resourceViewModels
                .drop(1)
                .collect {
                    sendServiceChildrenChangedEvent()
                }
        }
    }

    private fun updateConsoleView(processHandler: ProcessHandler) {
        val console = createConsole(
            ConsoleKind.Normal,
            processHandler,
            project
        )
        Disposer.register(this@AspireAppHostViewModel, console)

        val previousConsole = consoleView
        if (previousConsole != null) {
            Disposer.dispose(previousConsole)
        }

        consoleView = console
    }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireAppHostViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        resourceViewModel: AspireResourceViewModel
    ) = resourceViewModel.getViewDescriptor()

    override fun getServices(project: Project) = resourceViewModels.value

    private fun selectAppHost() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .select(this, AspireMainServiceViewContributor2::class.java, true, true)
        }
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            this,
            AspireMainServiceViewContributor2::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceChildrenChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            this,
            AspireMainServiceViewContributor2::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}