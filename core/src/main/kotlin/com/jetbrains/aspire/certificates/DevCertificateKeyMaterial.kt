package com.jetbrains.aspire.certificates

import com.jetbrains.aspire.worker.dcp.AspireSessionServerTlsConfig
import org.jetbrains.annotations.ApiStatus
import java.security.KeyStore
import java.util.Base64

/**
 * The ASP.NET Core HTTPS development certificate together with its private key, loaded into an
 * in-memory key store.
 *
 * Transport-agnostic on purpose: the certificate code produces this, and whoever terminates TLS
 * maps it onto its own configuration (see `AspireSessionTlsConfig` for the embedded DCP server).
 *
 * @param keyStore a PKCS12 key store holding the certificate and its private key
 * @param keyAlias the alias of the key entry inside [keyStore]
 * @param password the password protecting both the store and the key entry
 */
@ApiStatus.Internal
class DevCertificateKeyMaterial(
    override val keyStore: KeyStore,
    override val keyAlias: String,
    private val password: CharArray,
): AspireSessionServerTlsConfig {
    override fun keyStorePassword(): CharArray = password.copyOf()
    override fun privateKeyPassword(): CharArray = password.copyOf()

    fun encodePublicCertificate(): String =
        Base64.getEncoder().encodeToString(keyStore.getCertificate(keyAlias).encoded)
}
