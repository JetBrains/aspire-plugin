@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.integration

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DATA
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.junit5.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.scriptingApi.runBlockingWithFlushing
import com.jetbrains.rider.test.shared.constants.TeamCityTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Tag(TeamCityTags.General.Season)
internal class PublishAppHostActionTest : PerTestSolutionTestBase() {
    override fun modifyOpenSolutionParams(params: OpenSolutionParams) {
        super.modifyOpenSolutionParams(params)
        params.restoreNuGetPackages = true
        params.waitForCaches = true
    }

    override val testLogManager by lazy { createAspireTestLogManager(traceScenarios, traceCategories) }

    @Test
    @Solution("DefaultAspireSolution")
    fun `publish generates expected Docker Compose`() {
        val appHostDirectory = activeSolutionDirectory.resolve("DefaultAspireSolution.AppHost")
        val appHostPath = appHostDirectory.resolve("DefaultAspireSolution.AppHost.csproj")
        val composePath = appHostDirectory.resolve("aspire-output/docker-compose.yaml")
        assertFalse(composePath.exists())

        runBlockingWithFlushing("Publishing Aspire AppHost", 5.minutes) {
            val worker = AspireWorker.getInstance(project)
            val appHosts = worker.appHosts.first { hosts -> hosts.any { it.appHostPath == appHostPath } }
            val appHost = appHosts.single { it.appHostPath == appHostPath }
            val action = assertNotNull(ActionManager.getInstance().getAction("Aspire.Host.Publish"))
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(ASPIRE_APP_HOST_DATA, appHost.data.value)
                .build()
            val event =
                AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindowId = TerminalToolWindowFactory.TOOL_WINDOW_ID
            withContext(Dispatchers.EDT) {
                if (toolWindowManager.getToolWindow(toolWindowId) == null) {
                    toolWindowManager.registerToolWindow(toolWindowId) {}
                }
            }
            val tabsManager = TerminalToolWindowTabsManager.getInstance(project)
            val initialTabs = withContext(Dispatchers.EDT) { tabsManager.tabs.toSet() }

            try {
                withContext(Dispatchers.EDT) {
                    ActionUtil.performAction(action, event)
                }

                var tab = withContext(Dispatchers.EDT) { tabsManager.tabs.firstOrNull { it !in initialTabs } }
                while (tab == null) {
                    delay(100.milliseconds)
                    tab = withContext(Dispatchers.EDT) { tabsManager.tabs.firstOrNull { it !in initialTabs } }
                }
                tab.view.sessionState.first { it == TerminalViewSessionState.Terminated }

                assertTrue(composePath.isRegularFile(), "Aspire publish did not create $composePath")
                executeWithGold(testGoldFile) { printStream ->
                    printStream.print(composePath.readText())
                }
            } finally {
                withContext(NonCancellable) {
                    val createdTabs = withContext(Dispatchers.EDT) {
                        tabsManager.tabs.filter { it !in initialTabs }.onEach(tabsManager::closeTab)
                    }
                    createdTabs.forEach { it.view.coroutineScope.coroutineContext.job.join() }
                }
            }
        }
    }
}
