@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.aspire.actions.ASPIRE_RESOURCE_DATA
import com.jetbrains.aspire.rider.actions.dashboard.getSingleProjectModelEntity
import com.jetbrains.aspire.rider.actions.dashboard.isInsideDirectoryOfAny
import com.jetbrains.aspire.worker.AspireWorker

/**
 * Adds the matching [ASPIRE_RESOURCE_DATA] value to data contexts that contain a Rider project entity.
 *
 * The rule is called for every data context snapshot, so it only registers a lazy value
 * when at least one AppHost has project resources and defers the project entity resolution to the lazy computation.
 * The resources are looked up in the project path index of each AppHost instead of walking the resource tree.
 */
@Suppress("JetBrainsInternalApiUsage")
internal class AspireProjectResourceUiDataRule : UiDataRule {
    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val project = snapshot[CommonDataKeys.PROJECT] ?: return
        val worker = project.serviceIfCreated<AspireWorker>() ?: return
        if (worker.appHosts.value.all { it.projectResources.value.isEmpty() }) return

        sink.lazyValue(ASPIRE_RESOURCE_DATA) { dataMap ->
            val projectResources = worker.appHosts.value
                .map { it.projectResources.value }
                .filter { it.isNotEmpty() }
            if (projectResources.isEmpty()) return@lazyValue null

            val projectEntity = getSingleProjectModelEntity(dataMap, project) { file ->
                file.isInsideDirectoryOfAny(projectResources.asSequence().flatMap { it.keys })
            } ?: return@lazyValue null
            val projectPath = projectEntity.url?.toPath()
                ?: return@lazyValue null
            val resource = projectResources.firstNotNullOfOrNull { it[projectPath]?.firstOrNull() }
                ?: return@lazyValue null
            resource.data.value
        }
    }
}
