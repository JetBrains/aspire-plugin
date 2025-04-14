package com.jetbrains.rider.aspire.run.states

import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL
import com.jetbrains.rider.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.rider.aspire.util.DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN
import com.jetbrains.rider.aspire.util.DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY
import com.jetbrains.rider.aspire.util.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL

interface AspireHostProfileState {
    val environmentVariables: Map<String, String>
}

fun AspireHostProfileState.getDcpInstancePrefix() = environmentVariables[DCP_INSTANCE_ID_PREFIX]

fun AspireHostProfileState.getDashboardBrowserToken() = environmentVariables[DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN]

fun AspireHostProfileState.getResourceServiceEndpointUrl() =
    environmentVariables[ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL] ?: environmentVariables[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL]

fun AspireHostProfileState.getResourceServiceApiKey() = environmentVariables[DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY]