@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.resources.grpc

import com.intellij.libraries.grpc.netty.shaded.NettyChannelProviderRegistrationService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.net.ssl.ConfirmingTrustManager
import com.jetbrains.aspire.generated.dashboard.*
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
interface GrpcResourceClientFactory {
    fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?): AspireResourceClient
}

internal class GrpcResourceClientFactoryImpl : GrpcResourceClientFactory {
    override fun create(resourceServiceEndpointUrl: String, resourceServiceApiKey: String?): AspireResourceClient {
        return GrpcResourceClient(resourceServiceEndpointUrl, resourceServiceApiKey)
    }
}

internal class GrpcResourceClient(
    resourceServiceEndpointUrl: String,
    resourceServiceApiKey: String?
) : AspireResourceClient {
    companion object {
        private val LOG = logger<GrpcResourceClient>()
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

    override fun watchResources(appHostPath: AspireAppHostPath): Flow<AspireResourceUpdate> {
        val request = WatchResourcesRequest.getDefaultInstance()
        return stub
            .watchResources(request, metadata)
            .mapNotNull { it.toAspireResourceUpdate(appHostPath) }
    }

    override fun watchResourceConsoleLogs(resourceName: String): Flow<List<AspireResourceLogEntry>> {
        val request = WatchResourceConsoleLogsRequest.newBuilder()
            .setResourceName(resourceName)
            .build()
        return stub
            .watchResourceConsoleLogs(request, metadata)
            .map { it.toAspireResourceLogEntries() }
    }

    override suspend fun executeResourceCommand(request: AspireResourceCommandRequest): AspireResourceCommandResponse {
        val grpcRequest = ResourceCommandRequest.newBuilder()
            .setResourceName(request.resourceName)
            .setResourceType(request.resourceType)
            .setCommandName(request.commandName)
            .build()
        return stub.executeResourceCommand(grpcRequest, metadata).toAspireResourceCommandResponse()
    }

    override fun shutdown() {
        LOG.trace("Shutting down gRPC dashboard client")
        channel.shutdown()
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow()
            }
        } catch (_: InterruptedException) {
            channel.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
