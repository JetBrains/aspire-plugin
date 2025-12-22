package com.jetbrains.aspire.rider.run.states

import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.util.getAspireDashboardFrontendBrowserToken
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.aspire.util.getAspireResourceServiceEndpointUrl

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