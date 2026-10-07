package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.resources.grpc.AspireDashboardClientApi
import com.jetbrains.aspire.resources.grpc.GrpcResourceClientFactory

class MockGrpcResourceClientFactory : GrpcResourceClientFactory {
    val clients = mutableListOf<MockAspireDashboardClientApi>()

    override fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?): AspireDashboardClientApi {
        val client = MockAspireDashboardClientApi()
        clients.add(client)
        return client
    }

    val lastClient: MockAspireDashboardClientApi?
        get() = clients.lastOrNull()
}
