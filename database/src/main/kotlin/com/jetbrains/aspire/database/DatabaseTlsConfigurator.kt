package com.jetbrains.aspire.database

import com.intellij.database.dataSource.DataSourceSslConfiguration
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.remote.jdbc.helpers.JdbcSettings
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.HexFormat

internal class DatabaseTlsConfigurator(
    private val certificateDirectory: Path,
    private val exportCertificate: suspend () -> Result<String>?
) {
    companion object {
        private val LOG = logger<DatabaseTlsConfigurator>()
    }

    private val certificateMutex = Mutex()
    private var cachedCertificatePath: Path? = null

    suspend fun configure(dataSource: LocalDataSource, type: DatabaseType) {
        val url = dataSource.url ?: return
        val sslMode = getSslMode(url, type) ?: return

        try {
            val certificatePath = getOrSaveCertificate() ?: return
            val ssl = DataSourceSslConfiguration(true, sslMode)
            ssl.myCaCertPath = certificatePath.toString()
            dataSource.sslCfg = ssl
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.info("Unable to configure the development certificate for $type: ${e.message}")
            LOG.debug(e)
        }
    }

    private fun getSslMode(url: String, type: DatabaseType): JdbcSettings.SslMode? {
        val detector = DatabaseTlsUrlDetector.getInstance()
        return when (type) {
            DatabaseType.MONGO ->
                if (detector.isLocalMongoTlsConnection(url)) JdbcSettings.SslMode.VERIFY_FULL else null

            // The Redis JDBC helper of the Database plugin supports only REQUIRE and VERIFY_CA modes
            DatabaseType.REDIS ->
                if (detector.isLocalRedisTlsConnection(url)) JdbcSettings.SslMode.VERIFY_CA else null

            else -> null
        }
    }

    private suspend fun getOrSaveCertificate(): Path? = certificateMutex.withLock {
        cachedCertificatePath
            ?.takeIf { withContext(Dispatchers.IO) { Files.exists(it) } }
            ?.let { return@withLock it }

        val base64Certificate = exportCertificate()?.getOrThrow() ?: return@withLock null
        withContext(Dispatchers.IO) {
            saveCertificate(base64Certificate)
        }.also { cachedCertificatePath = it }
    }

    private fun saveCertificate(base64Certificate: String): Path {
        val bytes = Base64.getDecoder().decode(base64Certificate)
        val certificate = bytes.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val encoded = certificate.encoded
        val fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded))
        val certificatePath = certificateDirectory.resolve("$fingerprint.pem")
        val pemBody = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(encoded)
        val pem = "-----BEGIN CERTIFICATE-----\n$pemBody\n-----END CERTIFICATE-----\n"
        if (Files.exists(certificatePath) && Files.readString(certificatePath) == pem) return certificatePath

        Files.createDirectories(certificateDirectory)
        val temporaryPath = Files.createTempFile(certificateDirectory, "aspire-", ".pem")
        try {
            Files.writeString(temporaryPath, pem)
            try {
                Files.move(
                    temporaryPath,
                    certificatePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryPath, certificatePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
        return certificatePath
    }
}
