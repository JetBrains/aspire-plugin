package com.jetbrains.rider.aspire.run

import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path

data class AspireHostConfig(
    val name: String,
    //from env: DEBUG_SESSION_TOKEN
    val debugSessionToken: String,
    //from env: DEBUG_SESSION_PORT
    val debugSessionPort: Int,
    val debuggingMode: Boolean,
    //from env: DOTNET_RESOURCE_SERVICE_ENDPOINT_URL
    val resourceServiceEndpointUrl: String?,
    //from env: DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY
    val resourceServiceApiKey: String?,
    val aspireHostLifetime: Lifetime,
    val aspireHostProjectPath: Path,
    val aspireHostProjectUrl: String?,
    val aspireHostRunConfiguration: AspireHostConfiguration?
)