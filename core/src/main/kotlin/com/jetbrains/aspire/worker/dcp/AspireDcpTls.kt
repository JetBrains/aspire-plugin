package com.jetbrains.aspire.worker.dcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.certificates.DevCertificateKeyMaterial
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.settings.AspireSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves and caches the TLS material for the embedded DCP session servers.
 *
 * The ASP.NET Core dev certificate is machine-wide, so it is exported once per project and the same
 * [DevCertificateKeyMaterial] instance is shared across every per-AppHost [AspireSessionServer] (the key
 * store is used read-only during TLS handshakes, so sharing is safe).
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireDcpTls(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireDcpTls = project.service()
    }

    /**
     * @param tls key store + passwords the embedded server uses to terminate HTTPS
     * @param base64Cert base64-encoded public certificate handed to DCP via `DEBUG_SESSION_SERVER_CERTIFICATE`
     */
    data class DcpTlsMaterial(
        val base64Cert: String,
        val tls: DevCertificateKeyMaterial?
    )

    private val mutex = Mutex()

    private var cached: DcpTlsMaterial? = null

    /**
     * Returns the shared TLS material, computing it once on first use, or `null` to serve plain HTTP.
     *
     * `null` is returned when HTTPS is disabled in settings or the dev certificate is missing/untrusted,
     * so callers fall back to HTTP instead of failing the run.
     */
    suspend fun getOrComputeTlsMaterial(): DcpTlsMaterial? {
        if (!AspireSettings.getInstance().connectToDcpViaHttps) return null

        cached?.let { return it }

        return mutex.withLock {
            cached?.let { return it }

            val provider = DevCertificateProvider.getInstance() ?: return null
            if (!provider.checkDevCertificate(true, project).isTrusted) return null

            val base64Cert = provider.exportCertificate(true, project).getOrNull() ?: return null
            val tls = provider.exportCertificateWithPrivateKey(true, project).getOrNull()

            DcpTlsMaterial(base64Cert, tls).also { cached = it }
        }
    }
}
