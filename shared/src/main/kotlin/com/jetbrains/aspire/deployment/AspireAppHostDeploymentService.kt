package com.jetbrains.aspire.deployment

import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspirePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireAppHostDeploymentService {
    suspend fun prepareCommand(
        appHostId: AspireAppHostId,
        operation: AspireDeploymentOperation,
    ): AspireDeploymentCommandLine?
}

@ApiStatus.Internal
enum class AspireDeploymentOperation(val commandName: String) {
    Publish("publish"),
    Deploy("deploy"),
}

@ApiStatus.Internal
data class AspireDeploymentCommandLine(
    val appHostName: String,
    val command: List<String>,
    val workingDirectory: AspirePath,
)
