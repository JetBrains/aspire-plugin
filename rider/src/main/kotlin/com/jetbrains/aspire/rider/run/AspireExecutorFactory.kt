@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.jetbrains.aspire.run.cli.AspireCliEnvironment
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.utils.RiderEnvironmentAccessor
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal abstract class AspireExecutorFactory(
    private val project: Project,
    private val parameters: AspireRunConfigurationParameters
) : AsyncExecutorFactory {
    companion object {
        private const val DOTNET_ROOT = "DOTNET_ROOT"
    }

    protected suspend fun configureEnvironmentVariables(
        appHostMainFilePath : Path,
        envs: MutableMap<String, String>,
        activeRuntime: DotNetCoreRuntime
    ): EnvironmentVariableValues {
        val aspireWorker = AspireWorker.getInstance(project)

        aspireWorker.start()

        val dcpEnvironmentVariables = aspireWorker.getEnvironmentVariablesForDcpConnection()
        envs.putAll(dcpEnvironmentVariables)

        val appHost = requireNotNull(aspireWorker.getOrCreateAppHostByPath(appHostMainFilePath))

        // the browser url is not known yet at this point - the callers resolve it from the launch profile
        // after this call and rewrite it with `configureUrl`, so the returned `aspireHostProjectUrl` is unused
        val result = AspireCliEnvironment.configure(
            appHost = appHost,
            browserUrl = null,
            usePodmanRuntime = parameters.usePodmanRuntime,
            envs = envs
        )

        val dotnetPath = RiderEnvironmentAccessor.getInstance(project).findFileInSystemPath("dotnet")
        if (dotnetPath == null) {
            setDotnetRootPathVariable(envs, activeRuntime)
        }

        return EnvironmentVariableValues(result.browserToken)
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