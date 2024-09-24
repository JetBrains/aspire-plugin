package com.jetbrains.rider.aspire.run.states

import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.aspire.util.DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY
import com.jetbrains.rider.aspire.util.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL

interface AspireHostProfileState {
    val environmentVariables: Map<String, String>
}

fun AspireHostProfileState.getDebugSessionToken() = environmentVariables[DEBUG_SESSION_TOKEN]

fun AspireHostProfileState.getDebugSessionPort() = environmentVariables[DEBUG_SESSION_PORT]
    ?.substringAfter(':')
    ?.toInt()

fun AspireHostProfileState.getResourceServiceEndpointUrl() = environmentVariables[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL]

fun AspireHostProfileState.getResourceServiceApiKey() = environmentVariables[DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY]