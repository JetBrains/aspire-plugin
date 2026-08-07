package com.jetbrains.aspire.certificates

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    private val json by lazy { Json { ignoreUnknownKeys = true } }

    /**
     * Parses the output of `dotnet dev-certs https --check-trust-machine-readable` and analyzes it.
     *
     * Throws if the output contains a malformed certificate array.
     */
    fun analyze(output: String): DevCertificateDiagnostics = analyzeCertificates(parseCheckOutput(output))

    private fun parseCheckOutput(output: String): List<DevCertificate> {
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        if (start !in 0..<end) return emptyList()
        val jsonArray = output.substring(start, end + 1)
        return json.decodeFromString(jsonArray)
    }

    private fun analyzeCertificates(certificates: List<DevCertificate>): DevCertificateDiagnostics {
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

@Suppress("unused")
@ApiStatus.Internal
enum class DevCertificateTrustLevel {
    None,
    Partial,
    Full,
    Unknown;

    val isTrusted: Boolean
        get() = this == Partial || this == Full
}

@ApiStatus.Internal
@Serializable
data class DevCertificate(
    @SerialName("Thumbprint") val thumbprint: String? = null,
    @SerialName("Version") val version: Int = 0,
    @SerialName("TrustLevel") val trustLevel: DevCertificateTrustLevel = DevCertificateTrustLevel.Unknown
)

@ApiStatus.Internal
data class DevCertificateDiagnostics(
    val certificates: List<DevCertificate>,
    val result: DevCertificateCheckResult,
    val oldTrustedVersions: List<Int> = emptyList()
) {
    val requiresAttention: Boolean
        get() = !result.isTrusted || oldTrustedVersions.isNotEmpty()
}
