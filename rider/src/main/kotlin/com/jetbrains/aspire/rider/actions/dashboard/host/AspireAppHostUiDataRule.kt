@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.actions.dashboard.host

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DATA
import com.jetbrains.aspire.rider.actions.dashboard.getSingleProjectModelEntity
import com.jetbrains.aspire.rider.actions.dashboard.isInsideDirectoryOfAny
import com.jetbrains.aspire.worker.AspireWorker

/**
 * Adds the matching [ASPIRE_APP_HOST_DATA] value to data contexts that contain a Rider AppHost project entity.
 *
 * The rule is called for every data context snapshot, so it only registers a lazy value
 * when at least one AppHost is known and defers the project entity resolution to the lazy computation.
 */
@Suppress("JetBrainsInternalApiUsage")
internal class AspireAppHostUiDataRule : UiDataRule {
    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val project = snapshot[CommonDataKeys.PROJECT] ?: return
        val worker = project.serviceIfCreated<AspireWorker>() ?: return
        if (worker.appHosts.value.isEmpty()) return

        sink.lazyValue(ASPIRE_APP_HOST_DATA) { dataMap ->
            val appHosts = worker.appHosts.value
            if (appHosts.isEmpty()) return@lazyValue null

            val projectEntity = getSingleProjectModelEntity(dataMap, project) { file ->
                file.isInsideDirectoryOfAny(appHosts.asSequence().map { it.appHostPath })
            } ?: return@lazyValue null
            val projectPath = projectEntity.url?.toPath()
                ?: return@lazyValue null
            val appHost = appHosts.firstOrNull { it.appHostPath == projectPath }
                ?: return@lazyValue null
            appHost.data.value
        }
    }
}
