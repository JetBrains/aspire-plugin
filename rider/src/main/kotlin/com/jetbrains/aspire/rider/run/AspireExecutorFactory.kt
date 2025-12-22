package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.util.ASPIRE_ALLOW_UNSECURED_TRANSPORT
import com.jetbrains.aspire.util.ASPIRE_CONTAINER_RUNTIME
import com.jetbrains.aspire.util.ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN
import com.jetbrains.aspire.util.ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL
import com.jetbrains.aspire.util.ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY
import com.jetbrains.aspire.util.ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL
import com.jetbrains.aspire.util.ASPNETCORE_URLS
import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.util.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL
import com.jetbrains.aspire.util.generateDcpInstancePrefix
import com.jetbrains.aspire.util.getAspireAllowUnsecuredTransport
import com.jetbrains.aspire.util.getAspireContainerRuntime
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardUnsecuredAllowAnonymous
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.utils.RiderEnvironmentAccessor
import java.net.URI
import java.util.UUID
import kotlin.io.path.absolutePathString

internal abstract class AspireExecutorFactory(
    private val project: Project,
    private val parameters: AspireRunConfigurationParameters
) : AsyncExecutorFactory {
    companion object {
        private const val DOTNET_ROOT = "DOTNET_ROOT"
    }

    protected suspend fun configureEnvironmentVariables(
        envs: MutableMap<String, String>,
        activeRuntime: DotNetCoreRuntime
    ): EnvironmentVariableValues {
        val aspireWorker = AspireWorker.getInstance(project)

        aspireWorker.start()

        val dcpEnvironmentVariables = aspireWorker.getEnvironmentVariablesForDcpConnection()
        envs.putAll(dcpEnvironmentVariables)

        val dcpInstancePrefix = generateDcpInstancePrefix()
        envs[DCP_INSTANCE_ID_PREFIX] = dcpInstancePrefix

        val urls = requireNotNull(envs[ASPNETCORE_URLS])
        val isHttpUrl = !urls.contains("https")
        val allowUnsecuredTransport = envs.getAspireAllowUnsecuredTransport()

        //Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        //Set the `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL` environment variable if not specified to connect to the resource service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        //note: we have to replace `ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` with `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL`
        //otherwise the url won't be passed to the resource service
        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val aspireResourceServiceEndpoint = envs[ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL]
            if (!aspireResourceServiceEndpoint.isNullOrEmpty()) {
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] = aspireResourceServiceEndpoint
            } else {
                val resourceEndpointPort = NetworkUtils.findFreePort(47200)
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] =
                    if (useHttp) "http://localhost:$resourceEndpointPort"
                    else "https://localhost:$resourceEndpointPort"
            }
        }

        val allowAnonymousDashboard = envs.getAspireDashboardUnsecuredAllowAnonymous()

        //Set the `ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN` environment variable to open a dashboard without login
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        //Set the `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable to configure resource service API key
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        if (!allowAnonymousDashboard) {
            val apiKey = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY] = apiKey
        }

        //Set `ASPIRE_CONTAINER_RUNTIME` environment variable to `podman` if it is specified in the run parameters
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        val containerRuntime = envs.getAspireContainerRuntime()
        if (parameters.usePodmanRuntime && !containerRuntime.equals("podman", true)) {
            envs[ASPIRE_CONTAINER_RUNTIME] = "podman"
        }

        //Set the `ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable if not specified to connect to the resource service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        val otlpEndpointUrl = envs.getAspireDashboardOtlpEndpointUrl()
        if (otlpEndpointUrl.isNullOrEmpty()) {
            val otlpEndpointPort = NetworkUtils.findFreePort(47300)
            envs[ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL] =
                if (useHttp) "http://localhost:$otlpEndpointPort"
                else "https://localhost:$otlpEndpointPort"
        }

        val dotnetPath = RiderEnvironmentAccessor.getInstance(project).findFileInSystemPath("dotnet")
        if (dotnetPath == null) {
            setDotnetRootPathVariable(envs, activeRuntime)
        }

        return EnvironmentVariableValues(browserToken)
    }

    private fun setDotnetRootPathVariable(envs: MutableMap<String, String>, activeRuntime: DotNetCoreRuntime) {
        val dotnetRootPath = activeRuntime.cliExePath.parent

        val dotnetRootPathString = dotnetRootPath.absolutePathString()
        val dotnetToolsPathString = dotnetRootPath.resolve("tools").absolutePathString()
        val dotnetPaths =
            if (SystemInfo.isUnix) "$dotnetRootPathString:$dotnetToolsPathString"
            else "$dotnetRootPathString;$dotnetToolsPathString"

        val pathVariable = PathEnvironmentVariableUtil.getPathVariableValue()
        if (pathVariable != null) {
            envs[RiderEnvironmentAccessor.PATH_VARIABLE] =
                if (SystemInfo.isUnix) "$pathVariable:$dotnetPaths"
                else "$pathVariable;$dotnetPaths"
        } else {
            envs[RiderEnvironmentAccessor.PATH_VARIABLE] = dotnetPaths
        }

        val dotnetRootEnvironmentVariable = EnvironmentUtil.getValue(DOTNET_ROOT)
        if (dotnetRootEnvironmentVariable == null) {
            envs[DOTNET_ROOT] = dotnetRootPathString
        }
    }

    protected fun configureUrl(urlValue: String, browserToken: String): String {
        val url = URI(urlValue)
        val updatedUrl = URI(
            url.scheme,
            null,
            url.host,
            url.port,
            "/login",
            "t=${browserToken}",
            null
        )
        return updatedUrl.toString()
    }

    protected data class EnvironmentVariableValues(
        val browserToken: String?
    )
}