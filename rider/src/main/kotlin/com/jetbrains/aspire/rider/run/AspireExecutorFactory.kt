@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.jetbrains.aspire.util.getAspireSpecificEnvironmentVariables
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal abstract class AspireExecutorFactory(
    private val project: Project,
    private val parameters: AspireRiderRunConfigurationParameters
) : AsyncExecutorFactory {
    companion object {
        private const val DOTNET_ROOT = "DOTNET_ROOT"
        private const val PATH = "PATH"
    }

    protected suspend fun putAdditionalEnvironmentVariables(
        envs: MutableMap<String, String>,
        appHostMainFilePath: Path,
        activeRuntime: DotNetCoreRuntime
    ) {
        val aspireWorker = AspireWorker.getInstance(project)
        val dcpEnvironmentVariables = aspireWorker.startAppHostSessionServer(appHostMainFilePath)
        envs.putAll(dcpEnvironmentVariables)

        val appHost = requireNotNull(aspireWorker.getOrCreateAppHostByPath(appHostMainFilePath))
        val aspireEnvironmentVariables = getAspireSpecificEnvironmentVariables(
            envs,
            appHost.browserToken,
            parameters.usePodmanRuntime
        )
        envs.putAll(aspireEnvironmentVariables)

        val dotnetPath = PathEnvironmentVariableUtil.findFirst("dotnet")
        if (dotnetPath == null) {
            putDotnetRootPathVariable(envs, activeRuntime)
        }
    }

    private fun putDotnetRootPathVariable(envs: MutableMap<String, String>, activeRuntime: DotNetCoreRuntime) {
        val dotnetRootPath = activeRuntime.cliExePath.parent

        val dotnetRootPathString = dotnetRootPath.absolutePathString()
        val dotnetToolsPathString = dotnetRootPath.resolve("tools").absolutePathString()
        val dotnetPaths =
            if (SystemInfo.isUnix) "$dotnetRootPathString:$dotnetToolsPathString"
            else "$dotnetRootPathString;$dotnetToolsPathString"

        val pathVariable = PathEnvironmentVariableUtil.getPathVariableValue()
        if (pathVariable != null) {
            envs[PATH] =
                if (SystemInfo.isUnix) "$pathVariable:$dotnetPaths"
                else "$pathVariable;$dotnetPaths"
        } else {
            envs[PATH] = dotnetPaths
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
}
