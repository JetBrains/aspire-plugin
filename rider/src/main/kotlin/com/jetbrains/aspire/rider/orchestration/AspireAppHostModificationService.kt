@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.orchestration

import VerboseIndentingLogger
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.PsiTreeWalker
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.RiderTextContent
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.session.CsIndentingNaiveRulesSet
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.session.RiderIndentingEditorSettings
import com.jetbrains.rider.languages.fileTypes.csharp.kotoparser.session.calculateCsIndent
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Service responsible for modifying AppHost files in .NET Aspire solutions,
 * providing functionality to insert `AddProject` methods into `AppHost.cs` files
 * to configure Aspire host applications correctly.
 */
@Service(Service.Level.PROJECT)
internal class AspireAppHostModificationService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireAppHostModificationService = project.service()
        private val LOG = logger<AspireAppHostModificationService>()

        private const val APP_HOST_FILE = "AppHost.cs"
        private const val PROGRAM_FILE = "Program.cs"
        private const val ADD_PROJECT_METHOD = ".AddProject("
        private const val CREATE_BUILDER_METHOD = ".CreateBuilder("
    }

    /**
     * Modify the `AppHost` project of the Aspire solution.
     *
     * This method can install required nuget packages and modify the `AppHost` project main file.
     *
     * @param hostProjectPath a file path to an Aspire AppHost project file
     * @param projectEntities a list of projects with which the `AppHost` should be modified
     * @return a flag whether the `AppHost` was modified
     */
    suspend fun modifyAppHost(hostProjectPath: Path, projectEntities: List<ProjectModelEntity>): Boolean {
        if (projectEntities.isEmpty()) return false

        val appHostFile = findAppHostFile(hostProjectPath)
        if (appHostFile == null) {
            LOG.warn("Unable to find AppHost.cs (or Program.cs) file in the host project")
            return false
        }

        val linesToInsert = modifyAppHostWithHandlers(projectEntities)

        return readAndEdtWriteAction {
            val document = appHostFile.findDocument()
            if (document == null) {
                LOG.warn("Unable to find AppHost.cs (or Program.cs) file document")
                return@readAndEdtWriteAction value(false)
            }

            val text = document.text

            val lastAddProjectIndex = text.lastIndexOf(ADD_PROJECT_METHOD)
            var semicolonIndex = if (lastAddProjectIndex == -1) {
                val builderIndex = text.indexOf(CREATE_BUILDER_METHOD)
                if (builderIndex == -1) {
                    LOG.info("Unable to find a method for creating a distributed builder")
                    return@readAndEdtWriteAction value(false)
                }
                text.indexOf(';', builderIndex)
            } else {
                text.indexOf(';', lastAddProjectIndex)
            }

            if (semicolonIndex == -1) {
                LOG.info("Unable to find an index to insert an `AddProject` method")
                return@readAndEdtWriteAction value(false)
            }

            val psiFile = appHostFile.findPsiFile(project)

            writeCommandAction(project, AspireRiderBundle.message("write.command.insert.into.app.host")) {
                val indent =
                    if (psiFile != null) calculateIndent(psiFile, document, semicolonIndex)
                    else ""

                var methodsWereInserted = false
                for (lineToInsert in linesToInsert) {
                    if (text.contains(lineToInsert)) continue

                    val textToInsert = buildString {
                        append('\n')
                        append('\n')
                        append(indent)
                        append(lineToInsert)
                    }
                    document.insertString(semicolonIndex + 1, textToInsert)
                    semicolonIndex += textToInsert.length
                    methodsWereInserted = true
                }

                return@writeCommandAction methodsWereInserted
            }
        }
    }

    private fun findAppHostFile(hostProjectPath: Path): VirtualFile? {
        val appHostFilePath = hostProjectPath.parent.resolve(APP_HOST_FILE)
        if (appHostFilePath.exists()) {
            return VirtualFileManager.getInstance().findFileByNioPath(appHostFilePath)
        }

        val programFilePath = hostProjectPath.parent.resolve(PROGRAM_FILE)
        if (programFilePath.exists()) {
            return VirtualFileManager.getInstance().findFileByNioPath(programFilePath)
        }

        return null
    }

    private suspend fun modifyAppHostWithHandlers(projectEntities: List<ProjectModelEntity>): MutableList<String> {
        val linesToInsert = mutableListOf<String>()
        val projectsByType = projectEntities.groupBy { getProjectType(it) }
        for ((projectType, projects) in projectsByType) {
            if (projectType == null) continue
            val handler = AspireProjectOrchestrationHandler.getHandlerForType(projectType)
            if (handler != null) {
                val lines = handler.modifyAppHost(projects, project)
                linesToInsert.addAll(lines)
            } else {
                LOG.debug { "No handler found for project type: $projectType" }
            }
        }
        return linesToInsert
    }

    private fun calculateIndent(psiFile: PsiFile, document: Document, offset: Int): String {
        val manager = PsiDocumentManager.getInstance(project)
        manager.commitDocument(document)
        val treeWalker = PsiTreeWalker(psiFile)
        val riderTextContent = RiderTextContent(document)
        val indentSettings = RiderIndentingEditorSettings.getUserDefinedSettings(psiFile, psiFile.language)
        val indents = calculateCsIndent(
            CsIndentingNaiveRulesSet,
            treeWalker,
            riderTextContent,
            offset.toLong(),
            offset.toLong() + 1,
            indentSettings,
            VerboseIndentingLogger()
        )

        return indents?.firstOrNull() ?: ""
    }
}
