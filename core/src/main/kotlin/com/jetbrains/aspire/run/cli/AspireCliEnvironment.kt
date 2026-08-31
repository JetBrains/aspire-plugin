package com.jetbrains.aspire.run.cli

import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Fills in the environment variables `aspire` needs so the IDE can connect to the resource service,
 * the dashboard and the OTLP endpoint, and returns the resulting [AppHostEnvironment]
 */
@ApiStatus.Internal
object AspireCliEnvironment {
    private const val RESOURCE_SERVICE_BASE_PORT = 47200
    private const val OTLP_BASE_PORT = 47300

    data class Result(
        val appHostEnvironment: AppHostEnvironment,
        val browserToken: String?,
        val useHttp: Boolean
    )

    /**
     * Mutates [envs] in place with the Aspire/DCP environment variables and returns the derived
     * [AppHostEnvironment].
     */
    fun configure(
        appHost: AspireAppHost,
        browserUrl: String?,
        usePodmanRuntime: Boolean = false,
        envs: MutableMap<String, String>
    ): Result {
        envs[DCP_INSTANCE_ID_PREFIX] = appHost.dcpInstancePrefix

        val urls = envs[ASPNETCORE_URLS]
        val isHttpUrl = when {
            !urls.isNullOrEmpty() -> !urls.contains("https")
            !browserUrl.isNullOrEmpty() -> browserUrl.startsWith("http://")
            else -> false
        }
        val allowUnsecuredTransport = envs.getAspireAllowUnsecuredTransport()

        // Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        // Set the `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL` environment variable if not specified to connect to the resource service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        // note: we have to replace `ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` with `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL`
        // otherwise the url won't be passed to the resource service
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

        // Set the `ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN` environment variable to open a dashboard without login
        // (skipped when the dashboard is configured to allow anonymous access)
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = appHost.browserToken
            envs[ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        // Set the `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable to configure the resource service
        // API key used by the IDE gRPC client (skipped when anonymous access is allowed)
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        var apiKey: String? = null
        if (!allowAnonymousDashboard) {
            apiKey = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY] = apiKey
        }

        // Set `ASPIRE_CONTAINER_RUNTIME` environment variable to `podman` if it is specified in the run parameters
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        val containerRuntime = envs.getAspireContainerRuntime()
        if (usePodmanRuntime && !containerRuntime.equals("podman", true)) {
            envs[ASPIRE_CONTAINER_RUNTIME] = "podman"
        }

        // Set the `ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable if not specified to collect telemetry
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
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
