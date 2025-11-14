@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.manifest

import com.intellij.execution.util.ExecUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * A service responsible for the generation of an Aspire manifest file.
 *
 * This service handles the creation of an Aspire manifest file (`aspire-manifest.json`) for a given Aspire Host project.
 */
@Service(Service.Level.PROJECT)
internal class ManifestService(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<ManifestService>()
        private val LOG = logger<ManifestService>()

        private const val MANIFEST_FILE_NAME = "aspire-manifest.json"
    }

    /**
     * Generates an Aspire manifest file for the given Aspire host project.
     *
     * The method executes the `dotnet run --publisher manifest` command on a provided host project.
     *
     * @param hostProjectPath the path of the Aspire host project for which the manifest needs to be generated
     */
    suspend fun generateManifest(hostProjectPath: Path) {
        LOG.info("Generating Aspire manifest")

        withBackgroundProgress(project, AspireBundle.message("progress.generating.aspire.manifest")) {
            val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            if (runtime == null) {
                LOG.warn("Unable to find .NET runtime")
                return@withBackgroundProgress
            }

            val commandLine = runtime.createCommandLine(
                listOf(
                    "run",
                    "--publisher",
                    "manifest",
                    "--output-path",
                    MANIFEST_FILE_NAME
                )
            )
            val directoryPath = hostProjectPath.parent
            commandLine.workDirectory = directoryPath.toFile()
            LOG.debug { commandLine.commandLineString }

            val output = ExecUtil.execAndGetOutput(commandLine)
            if (output.checkSuccess(LOG)) {
                LOG.info("Aspire manifest is generated")

                val manifestPath = directoryPath.resolve(MANIFEST_FILE_NAME)
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