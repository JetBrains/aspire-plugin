package com.jetbrains.aspire.rider.util

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import java.nio.file.Path

/**
 * Returns the path to the .NET CLI of the active .NET runtime.
 */
internal fun getActiveDotNetCliPath(project: Project): Result<Path> {
    val cliPath = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value?.cliExePath
        ?: return Result.failure(ExecutionException("Unable to find active .NET runtime"))

    return Result.success(cliPath)
}
