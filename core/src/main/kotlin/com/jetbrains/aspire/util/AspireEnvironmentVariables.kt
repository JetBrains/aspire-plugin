package com.jetbrains.aspire.util

import com.intellij.util.NetworkUtils
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

const val DEBUG_SESSION_TOKEN = "DEBUG_SESSION_TOKEN"
const val DEBUG_SESSION_PORT = "DEBUG_SESSION_PORT"
const val DEBUG_SESSION_SERVER_CERTIFICATE = "DEBUG_SESSION_SERVER_CERTIFICATE"
const val DCP_INSTANCE_ID_PREFIX = "DCP_INSTANCE_ID_PREFIX"
const val ASPNETCORE_URLS = "ASPNETCORE_URLS"

const val ASPIRE_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS = "ASPIRE_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS"
const val DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS = "DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS"
fun Map<String, String>.getAspireDashboardUnsecuredAllowAnonymous() : Boolean =
    (get(ASPIRE_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS) ?: get(DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS))
        ?.equals("true", true) == true

const val ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN = "ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN"
const val DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN = "DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN"
fun Map<String, String>.getAspireDashboardFrontendBrowserToken() =
    get(ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN) ?: get(DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN)

const val ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY = "ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY"
const val DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY = "DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY"
fun Map<String, String>.getAspireDashboardResourceServiceApiKey() =
    get(ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY) ?: get(DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY)

const val ASPIRE_ALLOW_UNSECURED_TRANSPORT = "ASPIRE_ALLOW_UNSECURED_TRANSPORT"
fun Map<String, String>.getAspireAllowUnsecuredTransport() : Boolean =
    get(ASPIRE_ALLOW_UNSECURED_TRANSPORT)?.equals("true", true) == true

const val ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL = "ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL"
const val DOTNET_RESOURCE_SERVICE_ENDPOINT_URL = "DOTNET_RESOURCE_SERVICE_ENDPOINT_URL"
fun Map<String, String>.getAspireResourceServiceEndpointUrl() =
    get(ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL) ?: get(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)

const val ASPIRE_CONTAINER_RUNTIME = "ASPIRE_CONTAINER_RUNTIME"
const val DOTNET_ASPIRE_CONTAINER_RUNTIME = "DOTNET_ASPIRE_CONTAINER_RUNTIME"
fun Map<String, String>.getAspireContainerRuntime() =
    get(ASPIRE_CONTAINER_RUNTIME) ?: get(DOTNET_ASPIRE_CONTAINER_RUNTIME)

const val ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL = "ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL"
const val DOTNET_DASHBOARD_OTLP_ENDPOINT_URL = "DOTNET_DASHBOARD_OTLP_ENDPOINT_URL"
fun Map<String, String>.getAspireDashboardOtlpEndpointUrl() =
    get(ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL) ?: get(DOTNET_DASHBOARD_OTLP_ENDPOINT_URL)


@ApiStatus.Internal
fun getAspireSpecificEnvironmentVariables(
    originalVariables: Map<String, String>,
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
    if (!allowAnonymousDashboard) {
        put(ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN, UUID.randomUUID().toString())
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

private fun localhostUrl(useHttp: Boolean, port: Int): String =
    if (useHttp) "http://localhost:$port" else "https://localhost:$port"