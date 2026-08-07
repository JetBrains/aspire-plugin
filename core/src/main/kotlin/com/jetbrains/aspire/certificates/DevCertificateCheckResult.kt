package com.jetbrains.aspire.certificates

import org.jetbrains.annotations.ApiStatus

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