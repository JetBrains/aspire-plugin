package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.grpc.GrpcResourceClientFactory

internal class MockGrpcResourceClientFactory : GrpcResourceClientFactory {
    val clients = mutableListOf<MockAspireResourceClient>()

    override fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?): AspireResourceClient {
        val client = MockAspireResourceClient()
        clients.add(client)
        return client
    }

    val lastClient: MockAspireResourceClient?
        get() = clients.lastOrNull()
}
