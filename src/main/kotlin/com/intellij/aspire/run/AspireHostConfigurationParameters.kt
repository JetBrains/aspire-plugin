package com.intellij.aspire.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.BrowserStarter
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.run.configurations.project.getRunOptions
import org.jdom.Element

class AspireHostConfigurationParameters(
    private val project: Project,
    var projectFilePath: String,
    var trackUrl: Boolean,
    var startBrowserParameters: DotNetStartBrowserParameters
) {
    companion object {
        private const val PROJECT_FILE_PATH = "PROJECT_FILE_PATH"
        private const val TRACK_URL = "TRACK_URL"
    }

    val startBrowserAction: (ExecutionEnvironment, RunProfile, ProcessHandler) -> Unit =
        { _, runProfile, processHandler ->
            if (startBrowserParameters.startAfterLaunch && runProfile is RunConfiguration) {
                val startBrowserSettings = StartBrowserSettings().apply {
                    isSelected = startBrowserParameters.startAfterLaunch
                    url = startBrowserParameters.url
                    browser = startBrowserParameters.browser
                    isStartJavaScriptDebugger = startBrowserParameters.withJavaScriptDebugger
                }
                BrowserStarter(runProfile, startBrowserSettings, processHandler).start()
            }
        }

    fun validate() {
        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull
        if (project.solution.isLoaded.valueOrNull != true || runnableProjects == null) {
            throw RuntimeConfigurationError(DotNetProjectConfigurationParameters.SOLUTION_IS_LOADING)
        }
        val project = runnableProjects.singleOrNull {
            it.projectFilePath == projectFilePath && AspireHostConfigurationType.isTypeApplicable(it.kind)
        } ?: throw RuntimeConfigurationError(DotNetProjectConfigurationParameters.PROJECT_NOT_SPECIFIED)
        if (!project.problems.isNullOrEmpty()) {
            throw RuntimeConfigurationError(project.problems)
        }
    }

    fun readExternal(element: Element) {
        projectFilePath = JDOMExternalizerUtil.readField(element, PROJECT_FILE_PATH) ?: ""
        val trackUrlString = JDOMExternalizerUtil.readField(element, TRACK_URL) ?: ""
        trackUrl = trackUrlString != "0"
        startBrowserParameters = DotNetStartBrowserParameters.readExternal(element)
    }

    fun writeExternal(element: Element) {
        JDOMExternalizerUtil.writeField(element, PROJECT_FILE_PATH, projectFilePath)
        JDOMExternalizerUtil.writeField(element, TRACK_URL, if (trackUrl) "1" else "0")
        startBrowserParameters.writeExternal(element)
    }
}

fun AspireHostConfigurationParameters.setUpFromRunnableProject(project: RunnableProject) {
    projectFilePath = project.projectFilePath
    trackUrl = true
    val runOptions = project.getRunOptions()
    val startBrowserUrl = runOptions.startBrowserUrl
    if (startBrowserUrl.isNotEmpty()) {
        startBrowserParameters.apply {
            url = startBrowserUrl
            startAfterLaunch = runOptions.launchBrowser
        }
    }
}