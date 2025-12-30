@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.orchestration

import VerboseIndentingLogger
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

/**
 * Service responsible for modifying AppHost files in .NET Aspire solutions,
 * providing functionality to insert AddProject methods into AppHost.cs files
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
     * Inserts `AddProject` methods into the `AppHost` file of the Aspire host project.
     *
     * @param hostProjectPath a file path to an Aspire AppHost project file
     * @param projects a list of project file paths to be integrated into the host project
     * @return a flag whether the methods were inserted into the `AppHost` file
     */
    suspend fun insertProjectsIntoAppHostFile(hostProjectPath: Path, projects: List<Path>): Boolean {
        if (projects.isEmpty()) return false

        val appHostFile = findAppHostFile(hostProjectPath)
        if (appHostFile == null) {
            LOG.warn("Unable to find AppHost.cs (or Program.cs) file in the host project")
            return false
        }

        val projectNames = projects.map { it.nameWithoutExtension }
        val methodsToInsert = createAddProjectMethodsToInsert(projectNames)
        if (methodsToInsert.isEmpty()) return false

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
                for (methodToInsert in methodsToInsert) {
                    if (text.contains(methodToInsert)) continue

                    val textToInsert = buildString {
                        append('\n')
                        append('\n')
                        append(indent)
                        append(methodToInsert)
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

    private fun createAddProjectMethodsToInsert(projectNames: List<String>) = buildList {
        for (projectName in projectNames.sorted()) {
            val projectType = projectName.replace('.', '_')
            val projectResourceName = projectName.replace('.', '-').lowercase()

            val methodToInsert = buildString {
                append("builder.AddProject<Projects.")
                append(projectType)
                append(">(\"")
                append(projectResourceName)
                append("\");")
            }
            add(methodToInsert)
        }
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
