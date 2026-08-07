package com.jetbrains.aspire.certificates

import org.testng.annotations.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevCertificateAnalyzerTest {
    private val analyzer = DevCertificateAnalyzer()

    private fun certificate(thumbprint: String, version: Int, trustLevel: String) =
        """{"Thumbprint":"$thumbprint","Version":$version,"TrustLevel":"$trustLevel"}"""

    @Test
    fun `no certificates`() {
        val diagnostics = analyzer.analyze("[]")

        assertEquals(DevCertificateCheckResult.NoCertificate, diagnostics.result)
        assertTrue(diagnostics.certificates.isEmpty())
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `single fully trusted certificate`() {
        val diagnostics = analyzer.analyze("[${certificate("AAA", 6, "Full")}]")

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertEquals(1, diagnostics.certificates.size)
        assertTrue(diagnostics.oldTrustedVersions.isEmpty())
        assertFalse(diagnostics.requiresAttention)
    }

    @Test
    fun `single partially trusted certificate`() {
        val diagnostics = analyzer.analyze("[${certificate("AAA", 6, "Partial")}]")

        assertEquals(DevCertificateCheckResult.PartiallyTrusted, diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `single untrusted certificate`() {
        val diagnostics = analyzer.analyze("[${certificate("AAA", 6, "None")}]")

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `multiple certificates with only one trusted`() {
        val diagnostics = analyzer.analyze(
            "[${certificate("AAA", 6, "Full")},${certificate("BBB", 6, "None")}]"
        )

        assertEquals(DevCertificateCheckResult.MultipleCertificatesIssue(2, 1), diagnostics.result)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `multiple certificates with all trusted`() {
        val diagnostics = analyzer.analyze(
            "[${certificate("AAA", 6, "Full")},${certificate("BBB", 6, "Partial")}]"
        )

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertFalse(diagnostics.requiresAttention)
    }

    @Test
    fun `trusted certificate of an old version requires attention`() {
        val diagnostics = analyzer.analyze("[${certificate("AAA", 4, "Full")}]")

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertEquals(listOf(4), diagnostics.oldTrustedVersions)
        assertTrue(diagnostics.requiresAttention)
    }

    @Test
    fun `old versions are reported without duplicates and sorted`() {
        val diagnostics = analyzer.analyze(
            "[${certificate("AAA", 5, "Full")}," +
                    "${certificate("BBB", 4, "Full")}," +
                    "${certificate("CCC", 5, "Partial")}]"
        )

        assertEquals(listOf(4, 5), diagnostics.oldTrustedVersions)
    }

    @Test
    fun `untrusted certificate of an old version is not reported as outdated`() {
        val diagnostics = analyzer.analyze("[${certificate("AAA", 4, "None")}]")

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertTrue(diagnostics.oldTrustedVersions.isEmpty())
    }

    @Test
    fun `certificate array surrounded by cli noise is parsed`() {
        val output = """
            Welcome to .NET!
            [${certificate("AAA", 6, "Full")}]
            Done.
        """.trimIndent()

        val diagnostics = analyzer.analyze(output)

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
        assertEquals("AAA", diagnostics.certificates.single().thumbprint)
    }

    @Test
    fun `output without a certificate array is treated as no certificates`() {
        val diagnostics = analyzer.analyze("No valid certificate found.")

        assertEquals(DevCertificateCheckResult.NoCertificate, diagnostics.result)
    }

    @Test
    fun `unknown trust level is not trusted`() {
        val diagnostics = analyzer.analyze("""[{"Thumbprint":"AAA","Version":6}]""")

        assertEquals(DevCertificateCheckResult.NotTrusted, diagnostics.result)
        assertEquals(DevCertificateTrustLevel.Unknown, diagnostics.certificates.single().trustLevel)
    }

    @Test
    fun `unknown fields are ignored`() {
        val diagnostics =
            analyzer.analyze("""[{"Thumbprint":"AAA","Version":6,"TrustLevel":"Full","NotExpired":true}]""")

        assertEquals(DevCertificateCheckResult.Trusted, diagnostics.result)
    }
}
