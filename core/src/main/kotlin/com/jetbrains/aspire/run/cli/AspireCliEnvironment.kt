package com.jetbrains.aspire.run.cli

import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import java.util.*

/**
 * The single shared configurator for the Aspire/DCP environment variables, used by both the CLI runner
 * (`AspireCliRunProfileState`) and the Rider run factory (`AspireExecutorFactory`).
 *
 * Merges the DCP connection env into [envs], fills in the environment variables the app host needs so the
 * IDE can connect to the resource service, the dashboard and the OTLP endpoint, and returns the resulting
 * [AppHostEnvironment] that drives the Services tool window (resource-service endpoint + api key, OTLP
 * endpoint, dashboard login URL).
 */
object AspireCliEnvironment {
    private const val RESOURCE_SERVICE_BASE_PORT = 47200
    private const val OTLP_BASE_PORT = 47300

    data class Result(
        val appHostEnvironment: AppHostEnvironment,
        val browserToken: String?,
        val useHttp: Boolean
    )

    /**
     * Merges [dcpEnvironmentVariables] into [envs], mutates [envs] in place with the Aspire/DCP environment
     * variables and returns the derived [AppHostEnvironment].
     */
    fun configure(
        appHost: AspireAppHost,
        browserUrl: String?,
        dcpEnvironmentVariables: Map<String, String>,
        usePodmanRuntime: Boolean,
        envs: MutableMap<String, String>
    ): Result {
        envs.putAll(dcpEnvironmentVariables)

        envs[DCP_INSTANCE_ID_PREFIX] = appHost.dcpInstancePrefix

        val urls = envs[ASPNETCORE_URLS]
        val isHttpUrl = when {
            !urls.isNullOrEmpty() -> !urls.contains("https")
            !browserUrl.isNullOrEmpty() -> browserUrl.startsWith("http://")
            else -> false
        }
        val allowUnsecuredTransport = envs.getAspireAllowUnsecuredTransport()

        // Automatically set ASPIRE_ALLOW_UNSECURED_TRANSPORT when the http protocol is used.
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        // Force DOTNET_RESOURCE_SERVICE_ENDPOINT_URL so the IDE can connect to the resource service.
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val aspireResourceServiceEndpoint = envs[ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL]
            if (!aspireResourceServiceEndpoint.isNullOrEmpty()) {
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] = aspireResourceServiceEndpoint
            } else {
                val resourceEndpointPort = NetworkUtils.findFreePort(RESOURCE_SERVICE_BASE_PORT)
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] = localhostUrl(useHttp, resourceEndpointPort)
            }
        }

        val allowAnonymousDashboard = envs.getAspireDashboardUnsecuredAllowAnonymous()

        // Dashboard front-end browser token (skip when the dashboard is configured to allow anonymous access).
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = appHost.browserToken
            envs[ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        // Resource-service API key used by the IDE gRPC client (skip when anonymous access is allowed).
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        var apiKey: String? = null
        if (!allowAnonymousDashboard) {
            apiKey = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY] = apiKey
        }

        // Set ASPIRE_CONTAINER_RUNTIME to `podman` when the run parameters request the podman runtime.
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        val containerRuntime = envs.getAspireContainerRuntime()
        if (usePodmanRuntime && !containerRuntime.equals("podman", true)) {
            envs[ASPIRE_CONTAINER_RUNTIME] = "podman"
        }

        // OTLP endpoint used to collect telemetry.
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#common-configuration
        if (envs.getAspireDashboardOtlpEndpointUrl().isNullOrEmpty()) {
            val otlpEndpointPort = NetworkUtils.findFreePort(OTLP_BASE_PORT)
            envs[ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL] = localhostUrl(useHttp, otlpEndpointPort)
        }

        val resourceServiceEndpointUrl = envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL]
        val otlpEndpointUrl = envs.getAspireDashboardOtlpEndpointUrl()
        val aspireHostProjectUrl = browserUrl?.takeIf { it.isNotBlank() }?.let { url ->
            if (browserToken != null) "$url/login?t=$browserToken" else url
        }

        val appHostEnvironment = AppHostEnvironment(
            resourceServiceEndpointUrl,
            apiKey,
            otlpEndpointUrl,
            aspireHostProjectUrl
        )

        return Result(appHostEnvironment, browserToken, useHttp)
    }

    private fun localhostUrl(useHttp: Boolean, port: Int): String =
        if (useHttp) "http://localhost:$port" else "https://localhost:$port"
}
