package me.rafaelldi.aspire.run

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseOnce
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.controls.*
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.run.configurations.project.getRunOptions
import java.io.File

class AspireHostConfigurationViewModel(
    private val lifetime: Lifetime,
    private val runnableProjectsModel: RunnableProjectsModel?,
    val projectSelector: ProjectSelector,
    val environmentVariablesEditor: EnvironmentVariablesEditor,
    separator: ViewSeparator,
    val urlEditor: TextEditor,
    val dotNetBrowserSettingsEditor: BrowserSettingsEditor
) : RunConfigurationViewModelBase() {
    override val controls: List<ControlBase> =
        listOf(
            projectSelector,
            environmentVariablesEditor,
            separator,
            urlEditor,
            dotNetBrowserSettingsEditor
        )

    private var isLoaded = false

    var trackEnvs = true
    var trackUrl = true

    init {
        disable()

        if (runnableProjectsModel != null) {
            projectSelector.bindTo(
                runnableProjectsModel,
                lifetime,
                { p -> p.kind == AspireRunnableProjectKinds.AspireHost },
                ::enable,
                ::handleProjectSelection
            )
        }

        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
    }

    private fun handleProjectSelection(project: RunnableProject) {
        if (!isLoaded) {
            return
        }

        val runOptions = project.getRunOptions()
        environmentVariablesEditor.envs.set(runOptions.environmentVariables)
        val startBrowserUrl = runOptions.startBrowserUrl
        if (startBrowserUrl.isNotEmpty()) {
            urlEditor.defaultValue.value = startBrowserUrl
            urlEditor.text.value = startBrowserUrl
            dotNetBrowserSettingsEditor.settings.value = BrowserSettings(runOptions.launchBrowser, false, null)
        }
    }

    private fun handleEnvValueChange() {
        projectSelector.project.valueOrNull?.let {
            val envs = it.environmentVariables.associate { pair -> pair.key to pair.value }.toSortedMap()
            val editorEnvs = environmentVariablesEditor.envs.value.toSortedMap()
            trackEnvs = envs == editorEnvs
        }
    }

    private fun handleUrlValueChange() {
        projectSelector.project.valueOrNull?.let {
            val runOptions = it.getRunOptions()
            trackUrl = urlEditor.text.value == runOptions.startBrowserUrl
        }
    }

    fun reset(
        projectFilePath: String,
        trackEnvs: Boolean,
        envs: Map<String, String>,
        trackUrl: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        isLoaded = false

        this.trackEnvs = trackEnvs
        this.trackUrl = trackUrl

        runnableProjectsModel?.projects?.adviseOnce(lifetime) { projectList ->
            dotNetBrowserSettingsEditor.settings.set(
                BrowserSettings(
                    dotNetStartBrowserParameters.startAfterLaunch,
                    dotNetStartBrowserParameters.withJavaScriptDebugger,
                    dotNetStartBrowserParameters.browser
                )
            )

            if (projectFilePath.isEmpty() || projectList.none {
                    it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
                }) {
                if (projectFilePath.isEmpty()) {
                    projectList.firstOrNull { it.kind == AspireRunnableProjectKinds.AspireHost }
                        ?.let { project ->
                            projectSelector.project.set(project)
                            isLoaded = true
                            handleProjectSelection(project)
                        }
                } else {
                    val fakeProjectName = File(projectFilePath).name
                    val fakeProject = RunnableProject(
                        fakeProjectName,
                        fakeProjectName,
                        projectFilePath,
                        RunnableProjectKinds.Unloaded,
                        emptyList(),
                        emptyList(),
                        null,
                        emptyList()
                    )
                    projectSelector.projectList.apply {
                        clear()
                        addAll(projectList + fakeProject)
                    }
                    projectSelector.project.set(fakeProject)
                }
            } else {
                projectList.singleOrNull {
                    it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
                }?.let { project ->
                    projectSelector.project.set(project)

                    val effectiveEnvs =
                        if (trackEnvs) project.environmentVariables.associate { it.key to it.value } else envs
                    environmentVariablesEditor.envs.set(effectiveEnvs)

                    val runOptions = project.getRunOptions()
                    val effectiveUrl = if (trackUrl) runOptions.startBrowserUrl else dotNetStartBrowserParameters.url
                    urlEditor.defaultValue.value = effectiveUrl
                    urlEditor.text.value = effectiveUrl
                }
            }

            isLoaded = true
        }
    }
}