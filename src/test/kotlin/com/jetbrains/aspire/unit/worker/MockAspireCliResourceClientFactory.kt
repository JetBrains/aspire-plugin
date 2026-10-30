package com.jetbrains.aspire.unit.worker

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.cli.AspireCliResourceClientFactory
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.worker.AspireAppHostId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path

internal class MockAspireCliResourceClientFactory : AspireCliResourceClientFactory {
    private val createdClients = MutableStateFlow<List<MockAspireResourceClient>>(emptyList())
    val clientsFlow: StateFlow<List<MockAspireResourceClient>> = createdClients.asStateFlow()

    val projects = mutableListOf<Project>()
    val appHostIds = mutableListOf<AspireAppHostId>()
    val appHostPaths = mutableListOf<Path>()

    override fun create(project: Project, appHostId: AspireAppHostId, appHostPath: Path): AspireResourceClient {
        projects.add(project)
        appHostIds.add(appHostId)
        appHostPaths.add(appHostPath)

        val client = MockAspireResourceClient()
        createdClients.update { it + client }
        return client
    }
}