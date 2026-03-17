@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.libraries.grpc.netty.shaded.NettyChannelProviderRegistrationService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.jetbrains.aspire.generated.dashboard.*
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import kotlinx.coroutines.flow.Flow
import java.net.URI
import java.util.concurrent.TimeUnit

internal class AspireDashboardClient(
    resourceServiceEndpointUrl: String,
    resourceServiceApiKey: String?
) {
    companion object {
        private val LOG = logger<AspireDashboardClient>()
        private const val API_KEY_HEADER = "x-resource-service-api-key"

        private fun createChannel(uri: URI): ManagedChannel {
            NettyChannelProviderRegistrationService.ensureChannelProviderRegistered()
            val builder = NettyChannelBuilder.forAddress(uri.host, uri.port)
            if (uri.scheme.equals("https", ignoreCase = true)) {
                builder.sslContext(buildSslContext())
            } else {
                builder.usePlaintext()
            }
            return builder.build()
        }

        private fun buildSslContext(): SslContext {
            return GrpcSslContexts
                .configure(SslContextBuilder.forClient(), SslProvider.JDK)
                .trustManager(
                    ConfirmingTrustManager.createForStorage(
                        CertificateManager.DEFAULT_PATH,
                        CertificateManager.DEFAULT_PASSWORD
                    )
                )
                .build()
        }
    }

    private val channel: ManagedChannel
    private val stub: DashboardServiceGrpcKt.DashboardServiceCoroutineStub
    private val metadata: Metadata

    init {
        val uri = URI.create(resourceServiceEndpointUrl)
        channel = createChannel(uri)

        stub = DashboardServiceGrpcKt.DashboardServiceCoroutineStub(channel)

        metadata = Metadata().apply {
            if (resourceServiceApiKey != null) {
                put(Metadata.Key.of(API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER), resourceServiceApiKey)
            }
        }

        LOG.trace { "Created gRPC dashboard client for $resourceServiceEndpointUrl" }
    }

    fun watchResources(): Flow<WatchResourcesUpdate> {
        val request = WatchResourcesRequest.getDefaultInstance()
        return stub.watchResources(request, metadata)
    }

    fun watchResourceConsoleLogs(resourceName: String): Flow<WatchResourceConsoleLogsUpdate> {
        val request = WatchResourceConsoleLogsRequest.newBuilder()
            .setResourceName(resourceName)
            .build()
        return stub.watchResourceConsoleLogs(request, metadata)
    }

    suspend fun executeResourceCommand(request: ResourceCommandRequest): ResourceCommandResponse {
        return stub.executeResourceCommand(request, metadata)
    }

    suspend fun getApplicationInformation(): ApplicationInformationResponse {
        val request = ApplicationInformationRequest.getDefaultInstance()
        return stub.getApplicationInformation(request, metadata)
    }

    fun shutdown() {
        LOG.trace("Shutting down gRPC dashboard client")
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
