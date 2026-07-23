package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.dcp.AspireSessionTlsConfig
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DevCertificateProvider {
    companion object {
        private val EP_NAME = ExtensionPointName<DevCertificateProvider>("com.jetbrains.aspire.devCertificateProvider")

        fun getInstance(): DevCertificateProvider? = EP_NAME.extensionList.firstOrNull()
    }

    suspend fun checkDevCertificate(useBundledRuntime: Boolean, project: Project): DevCertificateCheckResult

    suspend fun exportCertificate(useBundledRuntime: Boolean, project: Project): String?

    /**
     * Exports the development certificate and its private key for the embedded Ktor session host.
     * The public certificate used in the DCP environment remains available through [exportCertificate].
     */
    suspend fun exportTlsConfig(useBundledRuntime: Boolean, project: Project): AspireSessionTlsConfig?

}

@ApiStatus.Internal
sealed interface DevCertificateCheckResult {
    val isTrusted: Boolean

    data object Trusted : DevCertificateCheckResult {
        override val isTrusted = true
    }

    data object NoCertificate : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data object NotTrusted : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data object PartiallyTrusted : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data class MultipleCertificatesIssue(
        val count: Int,
        val trustedCount: Int
    ) : DevCertificateCheckResult {
        override val isTrusted = trustedCount > 0 && trustedCount == count
    }

    data object CheckFailed : DevCertificateCheckResult {
        override val isTrusted = false
    }
}
