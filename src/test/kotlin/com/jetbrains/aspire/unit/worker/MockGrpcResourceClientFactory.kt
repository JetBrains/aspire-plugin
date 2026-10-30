package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.grpc.GrpcResourceClientFactory
import com.jetbrains.aspire.worker.AspireAppHostId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class MockGrpcResourceClientFactory : GrpcResourceClientFactory {
    private val createdClients = MutableStateFlow<List<MockAspireResourceClient>>(emptyList())
    val clientsFlow: StateFlow<List<MockAspireResourceClient>> = createdClients.asStateFlow()

    val endpointUrls = mutableListOf<String>()
    val apiKeys = mutableListOf<String?>()
    val appHostIds = mutableListOf<AspireAppHostId>()

    override fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?, appHostId: AspireAppHostId): AspireResourceClient {
        endpointUrls.add(resourceServiceEndpointUrl)
        apiKeys.add(resourceServiceApiKey)
        appHostIds.add(appHostId)

        val client = MockAspireResourceClient()
        createdClients.update { it + client }
        return client
    }
}
