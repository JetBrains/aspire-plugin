@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.generated.dashboard.ApplicationInformationRequest
import com.jetbrains.aspire.generated.dashboard.ApplicationInformationResponse
import com.jetbrains.aspire.generated.dashboard.DashboardServiceGrpcKt
import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.generated.dashboard.WatchResourceConsoleLogsRequest
import com.jetbrains.aspire.generated.dashboard.WatchResourceConsoleLogsUpdate
import com.jetbrains.aspire.generated.dashboard.WatchResourcesRequest
import com.jetbrains.aspire.generated.dashboard.WatchResourcesUpdate
import com.jetbrains.aspire.util.exportCertificate
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class AspireDashboardClient private constructor(
    resourceServiceEndpointUrl: String,
    resourceServiceApiKey: String?,
    private val channel: ManagedChannel
) : Disposable {
    companion object {
        private val LOG = logger<AspireDashboardClient>()
        private const val API_KEY_HEADER = "x-resource-service-api-key"

        suspend fun create(
            project: Project,
            resourceServiceEndpointUrl: String,
            resourceServiceApiKey: String?
        ): AspireDashboardClient {
            return AspireDashboardClient(
                resourceServiceEndpointUrl,
                resourceServiceApiKey,
                createChannel(project, resourceServiceEndpointUrl)
            )
        }

        private suspend fun createChannel(project: Project, endpointUrl: String): ManagedChannel {
            val endpointUri = URI(endpointUrl)
            val scheme = endpointUri.scheme?.lowercase()
                ?: throw IllegalArgumentException("Aspire dashboard endpoint has no scheme: $endpointUrl")
            val host = endpointUri.host
                ?: throw IllegalArgumentException("Aspire dashboard endpoint has no host: $endpointUrl")
            val port = if (endpointUri.port != -1) endpointUri.port else defaultPort(scheme)

            val builder = NettyChannelBuilder.forAddress(host, port)
                .maxInboundMessageSize(16 * 1024 * 1024)

            when (scheme) {
                "http" -> builder.usePlaintext()
                "https" -> builder.sslContext(createSslContext(project))
                else -> throw IllegalArgumentException("Unsupported Aspire dashboard endpoint scheme '$scheme'")
            }

            return builder.build()
        }

        private fun defaultPort(scheme: String): Int = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> error("Unsupported Aspire dashboard endpoint scheme '$scheme'")
        }

        private suspend fun createSslContext(project: Project) =
            GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.JDK)
                .trustManager(createTrustManager(project))
                .build()

        private suspend fun createTrustManager(project: Project): X509TrustManager {
            val systemTrustManager = createTrustManager(null)
            val devCertificateTrustManager = createDevCertificateTrustManager(project)
            if (devCertificateTrustManager == null) {
                LOG.trace { "Falling back to the system trust store for Aspire dashboard gRPC" }
                return systemTrustManager
            }

            LOG.trace { "Using system and .NET dev certificate trust stores for Aspire dashboard gRPC" }
            return CompositeX509TrustManager(systemTrustManager, devCertificateTrustManager)
        }

        private suspend fun createDevCertificateTrustManager(project: Project): X509TrustManager? {
            val lifetimeDefinition = AspireService.getInstance(project).lifetime.createNested()
            return try {
                val exportedCertificate = exportCertificate(lifetimeDefinition.lifetime, project) ?: return null
                val certificate = parseCertificate(exportedCertificate)
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setCertificateEntry("aspire-dev-certificate", certificate)
                }
                createTrustManager(keyStore)
            } finally {
                lifetimeDefinition.terminate()
            }
        }

        private fun parseCertificate(encodedCertificate: String): X509Certificate {
            val bytes = Base64.getDecoder().decode(encodedCertificate)
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }

        private fun createTrustManager(keyStore: KeyStore?): X509TrustManager {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)

            return trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .singleOrNull()
                ?: error("Expected a single X509TrustManager")
        }
    }

    private val metadata = Metadata().apply {
        if (resourceServiceApiKey != null) {
            put(Metadata.Key.of(API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER), resourceServiceApiKey)
        }
    }
    private val stub = DashboardServiceGrpcKt.DashboardServiceCoroutineStub(channel)

    init {
        LOG.trace { "Created gRPC dashboard client for $resourceServiceEndpointUrl" }
    }

    fun watchResources(isReconnect: Boolean = false): Flow<WatchResourcesUpdate> {
        val request = WatchResourcesRequest.newBuilder()
            .setIsReconnect(isReconnect)
            .build()
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

    override fun dispose() {
        LOG.trace("Shutting down gRPC dashboard client")
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

private class CompositeX509TrustManager(
    private val primary: X509TrustManager,
    private val fallback: X509TrustManager
) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return primary.acceptedIssuers + fallback.acceptedIssuers
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted(chain, authType) { trustManager, certificates, type ->
            trustManager.checkClientTrusted(certificates, type)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted(chain, authType) { trustManager, certificates, type ->
            trustManager.checkServerTrusted(certificates, type)
        }
    }

    private inline fun checkTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        trust: (X509TrustManager, Array<out X509Certificate>, String) -> Unit
    ) {
        val certificates = chain ?: throw CertificateException("No server certificate chain was provided")
        val type = authType ?: throw CertificateException("No authentication type was provided")

        try {
            trust(primary, certificates, type)
        } catch (primaryError: CertificateException) {
            try {
                trust(fallback, certificates, type)
            } catch (fallbackError: CertificateException) {
                fallbackError.addSuppressed(primaryError)
                throw fallbackError
            }
        }
    }
}
