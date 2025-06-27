package com.jetbrains.rider.aspire.run.states

import com.jetbrains.rider.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.rider.aspire.util.getAspireDashboardFrontendBrowserToken
import com.jetbrains.rider.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.rider.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.rider.aspire.util.getAspireResourceServiceEndpointUrl

interface AspireHostProfileState {
    val environmentVariables: Map<String, String>
}

fun AspireHostProfileState.getDcpInstancePrefix() =
    environmentVariables[DCP_INSTANCE_ID_PREFIX]

fun AspireHostProfileState.getDashboardBrowserToken() =
    environmentVariables.getAspireDashboardFrontendBrowserToken()

fun AspireHostProfileState.getResourceServiceEndpointUrl() =
    environmentVariables.getAspireResourceServiceEndpointUrl()

fun AspireHostProfileState.getResourceServiceApiKey() =
    environmentVariables.getAspireDashboardResourceServiceApiKey()

fun AspireHostProfileState.getOtlpEndpointUrl() =
    environmentVariables.getAspireDashboardOtlpEndpointUrl()