package com.jetbrains.aspire.run

import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Fills in the environment variables `aspire` needs so the IDE can connect to the resource service,
 * the dashboard and the OTLP endpoint, and returns the resulting [AppHostEnvironment]
 */
@ApiStatus.Internal
object AspireEnvironment {
    private const val RESOURCE_SERVICE_BASE_PORT = 47200
    private const val OTLP_BASE_PORT = 47300

    data class Result(
        val appHostEnvironment: AppHostEnvironment,
        val browserToken: String?,
        val useHttp: Boolean
    )

    fun getAspireSpecificEnvironmentVariables(
        originalVariables: Map<String, String>,
        defaultBrowserToken: String,
        usePodmanRuntime: Boolean = false,
        baseResourceServicePort: Int = 47200,
        baseOtlpPort: Int = 47300
    ): Map<String, String> = buildMap {
        val urls = originalVariables[ASPNETCORE_URLS]
        val isHttpUrl = when {
            !urls.isNullOrEmpty() -> !urls.contains("https")
            else -> false
        }
        val allowUnsecuredTransport = originalVariables.getAspireAllowUnsecuredTransport()

        // Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            put(ASPIRE_ALLOW_UNSECURED_TRANSPORT, "true")
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        // Set the `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL` environment variable if not specified to connect to the resource service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        if (originalVariables.getAspireResourceServiceEndpointUrl().isNullOrEmpty()) {
            val resourceEndpointPort = NetworkUtils.findFreePort(baseResourceServicePort)
            put(ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL, localhostUrl(useHttp, resourceEndpointPort))
        }

        val allowAnonymousDashboard = originalVariables.getAspireDashboardUnsecuredAllowAnonymous()

        // Set the `ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN` environment variable to open a dashboard without login
        // (skipped when the dashboard is configured to allow anonymous access)
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = defaultBrowserToken
            put(ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN, browserToken)
        }

        // Set the `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable to configure the resource service
        // API key used by the IDE gRPC client (skipped when anonymous access is allowed)
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        var apiKey: String? = null
        if (!allowAnonymousDashboard) {
            apiKey = UUID.randomUUID().toString()
            put(ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY, apiKey)
        }

        // Set `ASPIRE_CONTAINER_RUNTIME` environment variable to `podman` if it is specified in the run parameters
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        val containerRuntime = originalVariables.getAspireContainerRuntime()
        if (usePodmanRuntime && !containerRuntime.equals("podman", true)) {
            put(ASPIRE_CONTAINER_RUNTIME, "podman")
        }

        // Set the `ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable if not specified to collect telemetry
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        if (originalVariables.getAspireDashboardOtlpEndpointUrl().isNullOrEmpty()) {
            val otlpEndpointPort = NetworkUtils.findFreePort(baseOtlpPort)
            put(ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL, localhostUrl(useHttp, otlpEndpointPort))
        }
    }

    /**
     * Mutates [envs] in place with the Aspire environment variables and returns the derived
     * [AppHostEnvironment].
     */
    fun configure(
        defaultBrowserToken: String,
        usePodmanRuntime: Boolean = false,
        envs: MutableMap<String, String>
    ): Result {
        val urls = envs[ASPNETCORE_URLS]
        val isHttpUrl = when {
            !urls.isNullOrEmpty() -> !urls.contains("https")
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
        if (envs.getAspireResourceServiceEndpointUrl().isNullOrEmpty()) {
            val resourceEndpointPort = NetworkUtils.findFreePort(RESOURCE_SERVICE_BASE_PORT)
            envs[ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL] = localhostUrl(useHttp, resourceEndpointPort)
        }

        val allowAnonymousDashboard = envs.getAspireDashboardUnsecuredAllowAnonymous()

        // Set the `ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN` environment variable to open a dashboard without login
        // (skipped when the dashboard is configured to allow anonymous access)
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        // see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = defaultBrowserToken
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

        val resourceServiceEndpointUrl = envs.getAspireResourceServiceEndpointUrl()
        val otlpEndpointUrl = envs.getAspireDashboardOtlpEndpointUrl()

        val appHostEnvironment = AppHostEnvironment(
            resourceServiceEndpointUrl,
            apiKey,
            otlpEndpointUrl,
            null
        )

        return Result(appHostEnvironment, browserToken, useHttp)
    }

    private fun localhostUrl(useHttp: Boolean, port: Int): String =
        if (useHttp) "http://localhost:$port" else "https://localhost:$port"
}
