@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.certificates

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.jetbrains.rider.web.DevCertificate
import com.jetbrains.rider.web.DevCertificateTrustLevel
import org.jetbrains.annotations.ApiStatus

/**
 * Analyzes the state of the ASP.NET Core HTTPS development certificates.
 */
@ApiStatus.Internal
@Service
class DevCertificateAnalyzer {
    companion object {
        const val CURRENT_ASPNET_CORE_CERTIFICATE_VERSION = 6
        const val MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION = 4

        fun getInstance(): DevCertificateAnalyzer = service()
    }

    /**
     * Analyzes the output of `dotnet dev-certs https --check-trust-machine-readable`.
     */
    fun analyze(certificates: List<DevCertificate>): DevCertificateDiagnostics {
        if (certificates.isEmpty()) {
            return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.NoCertificate)
        }

        val trustedCount = certificates.count { it.trustLevel.isTrusted }
        val fullyTrustedCount = certificates.count { it.trustLevel == DevCertificateTrustLevel.Full }
        val partiallyTrustedCount = certificates.count { it.trustLevel == DevCertificateTrustLevel.Partial }

        val result = when {
            certificates.size > 1 -> when (trustedCount) {
                certificates.size -> DevCertificateCheckResult.Trusted
                else -> DevCertificateCheckResult.MultipleCertificatesIssue(certificates.size, trustedCount)
            }

            trustedCount == 0 -> DevCertificateCheckResult.NotTrusted
            partiallyTrustedCount > 0 && fullyTrustedCount == 0 -> DevCertificateCheckResult.PartiallyTrusted
            else -> DevCertificateCheckResult.Trusted
        }

        val oldTrustedVersions = certificates
            .filter { it.trustLevel.isTrusted && it.version < CURRENT_ASPNET_CORE_CERTIFICATE_VERSION }
            .map { it.version }
            .distinct()
            .sorted()

        return DevCertificateDiagnostics(certificates, result, oldTrustedVersions)
    }
}


@ApiStatus.Internal
data class DevCertificateDiagnostics(
    val certificates: List<DevCertificate>,
    val result: DevCertificateCheckResult,
    val oldTrustedVersions: List<Int> = emptyList()
) {
    val requiresAttention: Boolean
        get() = !result.isTrusted || oldTrustedVersions.isNotEmpty()
}
