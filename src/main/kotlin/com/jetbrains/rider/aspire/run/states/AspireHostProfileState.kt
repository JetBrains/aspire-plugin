package com.jetbrains.rider.aspire.run.states

import com.jetbrains.rider.aspire.util.*

interface AspireHostProfileState {
    val environmentVariables: Map<String, String>
}

fun AspireHostProfileState.getDebugSessionToken() = environmentVariables[DEBUG_SESSION_TOKEN]

fun AspireHostProfileState.getDebugSessionPort() = environmentVariables[DEBUG_SESSION_PORT]
    ?.substringAfter(':')
    ?.toInt()

fun AspireHostProfileState.getDcpInstancePrefix() = environmentVariables[DCP_INSTANCE_ID_PREFIX]

fun AspireHostProfileState.getDashboardBrowserToken() = environmentVariables[DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN]

fun AspireHostProfileState.getResourceServiceEndpointUrl() = environmentVariables[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL]

fun AspireHostProfileState.getResourceServiceApiKey() = environmentVariables[DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY]