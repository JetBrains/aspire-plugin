package com.jetbrains.aspire.worker

class MockAspireDashboardClientFactory : AspireDashboardClientFactory {
    val clients = mutableListOf<MockAspireDashboardClientApi>()

    override fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?): AspireDashboardClientApi {
        val client = MockAspireDashboardClientApi()
        clients.add(client)
        return client
    }

    val lastClient: MockAspireDashboardClientApi?
        get() = clients.lastOrNull()
}
