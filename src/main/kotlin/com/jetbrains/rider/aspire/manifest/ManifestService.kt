@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.manifest

import com.intellij.execution.util.ExecUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class ManifestService(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<ManifestService>()
        private val LOG = logger<ManifestService>()

        private const val MANIFEST_FOLDER = "aspire-manifest"
        private const val MANIFEST_FILE_NAME = "manifest.json"
    }

    fun generateManifest(hostProjectPath: Path) {
        scope.launch(Dispatchers.Default) {
            withBackgroundProgress(project, AspireBundle.message("progress.generating.aspire.manifest")) {
                val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
                if (runtime == null) {
                    LOG.warn("Unable to find .NET runtime")
                    return@withBackgroundProgress
                }

                val commandLine = runtime.createCommandLine(
                    listOf(
                        "msbuild",
                        "/t:GenerateAspireManifest",
                        "/p:AspireManifestPublishOutputPath=$MANIFEST_FOLDER"
                    )
                )
                val directoryPath = hostProjectPath.parent
                commandLine.workDirectory = directoryPath.toFile()
                LOG.debug(commandLine.commandLineString)

                val output = ExecUtil.execAndGetOutput(commandLine)
                if (output.checkSuccess(LOG)) {
                    val manifestPath = directoryPath.resolve(MANIFEST_FOLDER).resolve(MANIFEST_FILE_NAME)
                    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(manifestPath.absolutePathString())
                    if (file != null && file.isValid) {
                        withContext(Dispatchers.EDT) {
                            PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
                        }
                    }
                } else {
                    withContext(Dispatchers.EDT) {
                        Notification(
                            "Aspire",
                            AspireBundle.message("notification.manifest.unable.to.generate"),
                            output.stderr,
                            NotificationType.WARNING
                        )
                            .notify(project)
                    }
                }
            }
        }
    }
}