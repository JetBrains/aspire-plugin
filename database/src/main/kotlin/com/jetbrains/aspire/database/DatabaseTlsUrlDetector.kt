package com.jetbrains.aspire.database

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Detects database connection URLs that point to a local host and require TLS.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class DatabaseTlsUrlDetector {
    companion object {
        fun getInstance(): DatabaseTlsUrlDetector = service()

        private val LOG = logger<DatabaseTlsUrlDetector>()
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
    }

    fun isLocalMongoTlsConnection(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (!uri.scheme.equals("mongodb", ignoreCase = true)) return false

            val tlsOptions = getQueryOptions(uri) { it.equals("tls", ignoreCase = true) || it.equals("ssl", ignoreCase = true) }
            if (!areAllOptionsEnabled(tlsOptions)) return false

            val hosts = uri.rawAuthority?.substringAfterLast('@')?.split(',') ?: return false
            hosts.all { isLocalHost(URI("mongodb://$it").host) }
        } catch (e: Exception) {
            LOG.debug("Failed to check if MongoDB connection is local TLS", e)
            false
        }
    }

    fun isLocalRedisTlsConnection(url: String): Boolean {
        return try {
            val jdbcPrefix = url.substringBefore(':')
            if (!jdbcPrefix.equals("jdbc", ignoreCase = true)) return false
            val uri = URI(url.substringAfter(':'))
            if (!uri.scheme.equals("redis", ignoreCase = true)) return false

            val sslOptions = getQueryOptions(uri) { it.equals("ssl", ignoreCase = true) }
            if (!areAllOptionsEnabled(sslOptions)) return false

            isLocalHost(uri.host)
        } catch (e: Exception) {
            LOG.debug("Failed to check if Redis connection is local TLS", e)
            false
        }
    }

    private fun getQueryOptions(uri: URI, keyFilter: (String) -> Boolean): List<String> =
        uri.rawQuery.orEmpty()
            .split('&')
            .filter { keyFilter(it.substringBefore('=')) }
            .map { it.substringAfter('=', "") }

    private fun areAllOptionsEnabled(values: List<String>): Boolean =
        values.isNotEmpty() && values.all { it.equals("true", ignoreCase = true) }

    private fun isLocalHost(host: String?): Boolean {
        val normalizedHost = host?.removeSurrounding("[", "]") ?: return false
        return LOCAL_HOSTS.any { it.equals(normalizedHost, ignoreCase = true) }
    }
}
