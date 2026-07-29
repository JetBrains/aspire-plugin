package com.jetbrains.aspire.run.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.dcp.AspireSessionServer
import com.jetbrains.aspire.worker.dcp.AspireSessionServerConfig
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Starts an embedded, pure-Kotlin DCP "IDE execution" server ([AspireSessionServer]) for an
 * [AspireAppHost] and produces the DCP environment variables `aspire run` needs to connect to it.
 *
 * The server binds a free loopback port and speaks plain HTTP (`tls = null`): loopback HTTP needs no
 * certificate/KeyStore, matching `connectToDcpViaHttps = false`.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireEmbeddedDcpHost {
    companion object {
        fun getInstance(project: Project): AspireEmbeddedDcpHost = project.service()

        private val LOG = logger<AspireEmbeddedDcpHost>()

        private const val BASE_PORT = 47100
    }

    /**
     * Starts a DCP server bound to [appHost] and returns a [Handle] carrying the DCP environment variables
     * and the means to [Handle.stop] the server when the run ends.
     */
    suspend fun start(appHost: AspireAppHost): Handle {
        val port = NetworkUtils.findFreePort(BASE_PORT)
        val token = UUID.randomUUID().toString()

        val server = AspireSessionServer(appHost, AspireSessionServerConfig(port, token, tls = null))
        server.start()

        val envVars = mapOf(
            DEBUG_SESSION_PORT to "localhost:${server.resolvedPort}",
            DEBUG_SESSION_TOKEN to token,
            DCP_INSTANCE_ID_PREFIX to appHost.dcpInstancePrefix,
        )

        LOG.trace { "Embedded DCP server for ${appHost.mainFilePath} bound on localhost:${server.resolvedPort}" }

        return Handle(server, envVars)
    }

    class Handle(private val server: AspireSessionServer, val envVars: Map<String, String>) {
        suspend fun stop() {
            server.stop()
        }
    }
}
