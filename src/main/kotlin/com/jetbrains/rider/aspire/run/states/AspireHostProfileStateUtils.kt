package com.jetbrains.rider.aspire.run.states

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import java.util.concurrent.atomic.AtomicInteger

internal fun ProcessHandler.addStoppedContainerRuntimeProcessListener(
    containerRuntimeNotificationCount: AtomicInteger,
    project: Project
) = addProcessListener(object : ProcessListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val text = decodeAnsiCommandsToString(event.text, outputType)
        checkRunningContainerRuntime(text)
    }

    private fun checkRunningContainerRuntime(text: String) {
        if (containsStoppedContainerRuntimeWarning(text)) {
            val counterValue = containerRuntimeNotificationCount.getAndIncrement()
            if (counterValue == 0) {
                application.invokeLater {
                    showNotificationAboutContainerRuntime(project)
                }
            }
        }
    }
})

private fun containsStoppedContainerRuntimeWarning(text: String) =
    text.contains("Ensure that Docker is running") ||
            text.contains("Ensure that Podman is running") ||
            text.contains("Ensure that the container runtime is running")

private fun showNotificationAboutContainerRuntime(project: Project) {
    Notification(
        "Aspire",
        AspireBundle.message("notification.unable.to.find.running.container.runtime"),
        AspireBundle.message("notification.ensure.that.container.runtime.is.running"),
        NotificationType.WARNING
    )
        .notify(project)
}