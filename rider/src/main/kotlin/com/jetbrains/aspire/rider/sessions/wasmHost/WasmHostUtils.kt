package com.jetbrains.aspire.rider.sessions.wasmHost

import com.intellij.execution.CantRunException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rider.debugger.RiderDebugRunner
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentHost
import com.jetbrains.rider.debugger.wasm.BrowserHub
import com.jetbrains.rider.debugger.wasm.ConnectedChromiumBrowser
import com.jetbrains.rider.debugger.wasm.browser.ChromiumDebuggableBrowser
import com.jetbrains.rider.debugger.wasm.client.WasmAppEnvironmentInfo
import com.jetbrains.rider.debugger.wasm.client.WasmClientRunProfile
import com.jetbrains.rider.debugger.wasm.util.BlazorWasmBrowserUtil
import com.jetbrains.rider.debugger.wasm.util.BlazorWasmCliOptionsProvider
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

private val LOG = Logger.getInstance("#com.jetbrains.aspire.sessionHost.projectLaunchers.WasmHostUtils")

internal suspend fun startBrowserAndAttach(
    browserHub: BrowserHub,
    browserHubLifetimeDef: LifetimeDefinition,
    browserStartSettings: StartBrowserSettings?,
    lifetime: Lifetime,
    project: Project
): ConnectedChromiumBrowser? {
    val browserInfo = startBrowser(browserStartSettings, project)
    if (browserInfo == null) {
        LOG.warn("Unable to obtain browser info")
        return null
    }

    val connectedBrowser = browserHub.attachToChrome(browserInfo)
    lifetime.onTermination {
        AspireService.getInstance(project).scope.launch(Dispatchers.EDT) {
            connectedBrowser.closeBrowser()
            browserHubLifetimeDef.terminate()
        }
    }

    return connectedBrowser
}

private suspend fun startBrowser(
    browserStartSettings: StartBrowserSettings?,
    project: Project
): ChromiumDebuggableBrowser? {
    try {
        val webBrowser = BlazorWasmCliOptionsProvider.resolveBrowser(browserStartSettings?.browser)
        BlazorWasmBrowserUtil.checkBrowserSupported(webBrowser)
        val appUrl = browserStartSettings?.url ?: return null

        return ChromiumDebuggableBrowser.launch(webBrowser, URI.create(appUrl), project)
    } catch (e: CantRunException) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.unable.to.launch.browser"),
            e.message ?: "",
            NotificationType.WARNING
        )
            .notify(project)
        return null
    }
}

internal suspend fun executeClient(
    projectFilePath: String,
    browserHub: BrowserHub,
    browserRefreshHost: BrowserRefreshAgentHost,
    connectedBrowser: ConnectedChromiumBrowser,
    browserStartSettings: StartBrowserSettings?,
    lifetime: Lifetime,
    project: Project
) {
    val appUrl = browserStartSettings?.url
    val outputAssemblyPath = extractProjectOutputAssemblyPath(projectFilePath, project)
    if (appUrl == null || outputAssemblyPath == null) {
        LOG.warn("Unable to create wasp app environment info")
        return
    }
    val appEnvInfo = WasmAppEnvironmentInfo(appUrl, outputAssemblyPath)

    connectedBrowser.availablePages.view(lifetime) { pageLifetime, _, page ->
        lifetime.coroutineScope.launch(Dispatchers.EDT) {
            val connectedPage = connectedBrowser.attachToPage(page.pageId)
            connectedPage.availableMonoRuntimes.view(pageLifetime) { runtimeLifetime, _, runtime ->
                runtimeLifetime.coroutineScope.launch(Dispatchers.EDT) {
                    val port = browserHub.startWorkerServer()
                    val clientProfile = WasmClientRunProfile(
                        page.title,
                        connectedBrowser.browserInfo.webBrowser,
                        connectedBrowser,
                        port,
                        page.pageId,
                        runtime,
                        appEnvInfo,
                        runtimeLifetime,
                        browserRefreshHost,
                        false
                    )

                    val environment = ExecutionEnvironmentBuilder.create(
                        project,
                        DefaultDebugExecutor.getDebugExecutorInstance(),
                        clientProfile
                    ).build()
                    environment.assignNewExecutionId()
                    val clientRunner =
                        ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, clientProfile) as? RiderDebugRunner
                    clientRunner?.execute(environment)
                }
            }
        }
    }
}

private suspend fun extractProjectOutputAssemblyPath(
    projectFilePath: String,
    project: Project
): String? {
    val outputAssemblyProperty = "TargetPath"
    val request = MSBuildEvaluator.PropertyRequest(projectFilePath, null, listOf(outputAssemblyProperty))
    val msBuildEvaluator = MSBuildEvaluator.getInstance(project)
    val properties = msBuildEvaluator.evaluatePropertiesSuspending(request)
    return properties[outputAssemblyProperty]
}