package com.jetbrains.aspire.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.util.decodeAnsiCommandsToString
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
class StoppedContainerRuntimeProcessListener(
    private val containerRuntimeNotificationCount: AtomicInteger,
    private val project: Project
) : ProcessListener {
    companion object {
        fun attach(
            processHandler: ProcessHandler,
            containerRuntimeNotificationCount: AtomicInteger,
            project: Project
        ) {
            val listener = StoppedContainerRuntimeProcessListener(containerRuntimeNotificationCount, project)
            processHandler.addProcessListener(listener)
        }
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val text = decodeAnsiCommandsToString(event.text, outputType)
        if (containsStoppedContainerRuntimeWarning(text) && containerRuntimeNotificationCount.getAndIncrement() == 0) {
            application.invokeLater {
                showNotificationAboutContainerRuntime(project)
            }
        }
    }

    private fun containsStoppedContainerRuntimeWarning(text: String) =
        text.contains("Ensure that Docker is running") ||
                text.contains("Ensure that Podman is running") ||
                text.contains("Ensure that the container runtime is running")

    private fun showNotificationAboutContainerRuntime(project: Project) {
        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.unable.to.find.running.container.runtime"),
            AspireCoreBundle.message("notification.ensure.that.container.runtime.is.running"),
            NotificationType.WARNING
        )
            .notify(project)
    }
}
