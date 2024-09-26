package com.jetbrains.rider.aspire.util

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.BrowserStarter
import com.intellij.ide.browsers.StartBrowserSettings
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters


fun getStartBrowserAction(
    browserUrl: String,
    params: DotNetStartBrowserParameters
): (ExecutionEnvironment, RunProfile, ProcessHandler) -> Unit =
    { _, runProfile, processHandler ->
        if (params.startAfterLaunch && runProfile is RunConfiguration) {
            val startBrowserSettings = StartBrowserSettings().apply {
                isSelected = params.startAfterLaunch
                url = browserUrl
                browser = params.browser
                isStartJavaScriptDebugger = params.withJavaScriptDebugger
            }
            BrowserStarter(runProfile, startBrowserSettings, processHandler).start()
        }
    }

fun getStartBrowserAction(
    runProfile: RunConfiguration,
    startBrowserSettings: StartBrowserSettings
): (ExecutionEnvironment, RunProfile, ProcessHandler) -> Unit =
    { _, _, processHandler ->
        BrowserStarter(runProfile, startBrowserSettings, processHandler).start()
    }