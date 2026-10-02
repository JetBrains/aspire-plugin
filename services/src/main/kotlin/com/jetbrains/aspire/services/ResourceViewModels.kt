package com.jetbrains.aspire.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.worker.AspireResourceModel
import com.jetbrains.aspire.worker.ResourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<AspireResourceViewModel>()

@ApiStatus.Internal
fun Flow<List<AspireResourceModel>>.toResourceViewModels(
    project: Project,
    cs: CoroutineScope,
    parent: Disposable
): StateFlow<List<AspireResourceViewModel>> =
    visibleResources(AspireSettings.getInstance().showHiddenResourcesFlow)
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
                        if (Disposer.tryRegister(parent, resourceVM)) {
                            add(resourceVM)
                        }
                    }
                }
            }.sortedWith(
                compareBy({ it.resource.data.value.type }, { it.resource.data.value.name })
            )
        }
        .stateIn(cs, SharingStarted.Eagerly, emptyList())

/**
 * Returns the resources to display at this level of the tree, updating when resources, their visibility,
 * or [showHiddenResources] change. A visible resource is returned as a single item: its children are
 * displayed separately beneath it. When a resource is hidden, its visible descendants take its place
 * at this level, even if multiple hidden ancestors need to be skipped.
 *
 * The setting is collected once per invocation and its current value is passed through the tree.
 * Changing it restarts the traversal; changes to resource data that do not affect visibility do not
 * restart the child traversal.
 */
@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<List<AspireResourceModel>>.visibleResources(showHiddenResources: Flow<Boolean>): Flow<List<AspireResourceModel>> =
    combine(this, showHiddenResources) { resources, showHidden -> resources to showHidden }
        .flatMapLatest { (resources, showHidden) -> resources.visibleResources(showHidden) }

@OptIn(ExperimentalCoroutinesApi::class)
private fun List<AspireResourceModel>.visibleResources(showHidden: Boolean): Flow<List<AspireResourceModel>> =
    if (isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(map { resource ->
            resource.data
                .map { data -> showHidden || (!data.isHidden && data.state != ResourceState.Hidden) }
                .distinctUntilChanged()
                .flatMapLatest { visible ->
                    if (visible) flowOf(listOf(resource))
                    else resource.childrenResources.flatMapLatest { children -> children.visibleResources(showHidden) }
                }
        }) { visibleResources -> visibleResources.flatMap { it } }
    }
