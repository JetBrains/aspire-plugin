package com.jetbrains.aspire.unit.worker

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.cli.AspireCliResourceClientFactory
import com.jetbrains.aspire.resources.AspireResourceClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class MockAspireCliResourceClientFactory : AspireCliResourceClientFactory {
    private val createdClients = MutableStateFlow<List<MockAspireResourceClient>>(emptyList())
    val clientsFlow: StateFlow<List<MockAspireResourceClient>> = createdClients.asStateFlow()

    val projects = mutableListOf<Project>()

    override fun create(project: Project): AspireResourceClient {
        projects.add(project)

        val client = MockAspireResourceClient()
        createdClients.update { it + client }
        return client
    }
}