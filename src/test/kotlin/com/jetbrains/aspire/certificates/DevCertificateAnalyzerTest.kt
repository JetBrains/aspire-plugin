package com.jetbrains.aspire.certificates

import com.jetbrains.rider.web.DevCertificate
import com.jetbrains.rider.web.DevCertificateTrustLevel
import org.testng.annotations.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UnstableApiUsage")
class DevCertificateAnalyzerTest {
    private val analyzer = DevCertificateAnalyzer()

    private fun certificate(thumbprint: String, version: Int, trustLevel: DevCertificateTrustLevel) =
        DevCertificate(thumbprint, version, trustLevel)

    @Test
    fun `no certificates`() {
        val certificates = emptyList<DevCertificate>()

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.NoCertificate, diagnostics.result)
        assertTrue(diagnostics.certificates.isEmpty())
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `single fully trusted certificate`() {
        val certificate = certificate("AAA", 6, DevCertificateTrustLevel.Full)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertEquals(1, diagnostics.certificates.size)
        assertTrue(diagnostics.oldTrustedVersions.isEmpty())
        assertFalse(diagnostics.requiresAttention)
    }

    @Test
    fun `single partially trusted certificate`() {
        val certificate = certificate("AAA", 6, DevCertificateTrustLevel.Partial)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.PartiallyTrusted, diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `single untrusted certificate`() {
        val certificate = certificate("AAA", 6, DevCertificateTrustLevel.None)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `multiple certificates with only one trusted`() {
        val trustedCertificate = certificate("AAA", 6, DevCertificateTrustLevel.Full)
        val untrustedCertificate = certificate("BBB", 6, DevCertificateTrustLevel.None)
        val certificates = listOf(trustedCertificate, untrustedCertificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.MultipleCertificatesIssue(2, 1), diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `multiple certificates with all trusted`() {
        val fullyTrustedCertificate = certificate("AAA", 6, DevCertificateTrustLevel.Full)
        val partiallyTrustedCertificate = certificate("BBB", 6, DevCertificateTrustLevel.Partial)
        val certificates = listOf(fullyTrustedCertificate, partiallyTrustedCertificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertFalse(diagnostics.requiresAttention)
    }

    @Test
    fun `trusted certificate of an old version requires attention`() {
        val certificate = certificate("AAA", 4, DevCertificateTrustLevel.Full)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertEquals(listOf(4), diagnostics.oldTrustedVersions)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `old versions are reported without duplicates and sorted`() {
        val versionFiveCertificate = certificate("AAA", 5, DevCertificateTrustLevel.Full)
        val versionFourCertificate = certificate("BBB", 4, DevCertificateTrustLevel.Full)
        val anotherVersionFiveCertificate = certificate("CCC", 5, DevCertificateTrustLevel.Partial)
        val certificates = listOf(versionFiveCertificate, versionFourCertificate, anotherVersionFiveCertificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(listOf(4, 5), diagnostics.oldTrustedVersions)
    }

    @Test
    fun `untrusted certificate of an old version is not reported as outdated`() {
        val certificate = certificate("AAA", 4, DevCertificateTrustLevel.None)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertTrue(diagnostics.oldTrustedVersions.isEmpty())
    }

    @Test
    fun `unknown trust level is not trusted`() {
        val certificate = certificate("AAA", 6, DevCertificateTrustLevel.Unknown)
        val certificates = listOf(certificate)

        val diagnostics = analyzer.analyze(certificates)

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertEquals(DevCertificateTrustLevel.Unknown, diagnostics.certificates.single().trustLevel)
    }
}
