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
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Service responsible for modifying ServiceDefaults in .NET Aspire projects,
 * providing functionality to insert AddServiceDefaults and MapDefaultEndpoints methods
 * into Program.cs files to configure project applications correctly.
 */
@Service(Service.Level.PROJECT)
internal class AspireServiceDefaultsModificationService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireServiceDefaultsModificationService = project.service()
        private val LOG = logger<AspireServiceDefaultsModificationService>()

        private const val PROGRAM_FILE = "Program.cs"
        private const val MAUI_PROGRAM_FILE = "MauiProgram.cs"
        private const val CREATE_BUILDER_METHOD = ".CreateBuilder("
        private const val CREATE_APPLICATION_BUILDER_METHOD = ".CreateApplicationBuilder("
        private const val BUILD_METHOD = ".Build("
        private const val ADD_SERVICE_DEFAULTS_METHOD = "builder.AddServiceDefaults();"
        private const val MAP_DEFAULT_ENDPOINTS = "app.MapDefaultEndpoints();"
        private const val USING_MICROSOFT_EXTENSION_HOSTING = "using Microsoft.Extensions.Hosting;"
    }

    /**
     * Inserts default Aspire methods (`AddServiceDefaults` and `MapDefaultEndpoints`)
     * into the `Program.cs` file of the requested projects.
     *
     * @param project a list of pairs of project file path and project entity representing projects into which
     * default Aspire methods need to be inserted
     * @return a flag whether the defaults methods were inserted into any of the projects
     */
    suspend fun insertAspireDefaultMethodsIntoProjects(project: List<Pair<Path, ProjectModelEntity?>>): Boolean {
        if (project.isEmpty()) return false

        var methodsWereInserted = false
        for ((projectFilePath, projectEntity) in project) {
            val descriptor = projectEntity?.descriptor
            val projectType = (descriptor as? RdProjectDescriptor)?.specificType
            val isWebProject = projectType == RdProjectType.Web
            val isMauiProject = projectType == RdProjectType.MAUI

            val projectProgramFilePath =
                if (!isMauiProject) projectFilePath.parent.resolve(PROGRAM_FILE)
                else projectFilePath.parent.resolve(MAUI_PROGRAM_FILE)
            if (!projectProgramFilePath.exists()) {
                LOG.info("Unable to find Program.cs file for a project ${projectFilePath.name}")
                continue
            }

            val projectProgramFile = VirtualFileManager.getInstance().findFileByNioPath(projectProgramFilePath)
            if (projectProgramFile == null) {
                LOG.warn("Unable to find Program.cs virtual file for a project ${projectFilePath.name}")
                continue
            }

            val insertionResult = insertAspireDefaultMethodsIntoProgramFile(projectProgramFile, isWebProject, isMauiProject)
            methodsWereInserted = methodsWereInserted || insertionResult
        }

        return methodsWereInserted
    }

    private suspend fun insertAspireDefaultMethodsIntoProgramFile(
        programFile: VirtualFile,
        isWebProject: Boolean,
        isMauiProject: Boolean
    ): Boolean = readAndEdtWriteAction {
        val document = programFile.findDocument()
        if (document == null) {
            LOG.warn("Unable to find Program.cs file document")
            return@readAndEdtWriteAction value(false)
        }

        val text = document.text

        var serviceDefaultsIndex =
            findSemicolonIndexAfterMethod(text, ADD_SERVICE_DEFAULTS_METHOD, CREATE_BUILDER_METHOD)
        if (serviceDefaultsIndex == -1) {
            serviceDefaultsIndex =
                findSemicolonIndexAfterMethod(text, ADD_SERVICE_DEFAULTS_METHOD, CREATE_APPLICATION_BUILDER_METHOD)
        }
        val defaultEndpointsIndex =
            if (isWebProject) findSemicolonIndexAfterMethod(text, MAP_DEFAULT_ENDPOINTS, BUILD_METHOD)
            else -1

        if (serviceDefaultsIndex == -1 && defaultEndpointsIndex == -1) {
            LOG.warn("Unable to find a place for the default methods in `Program.cs` file")
            return@readAndEdtWriteAction value(false)
        }

        val psiFile = programFile.findPsiFile(project)

        writeCommandAction(project, AspireRiderBundle.message("write.command.insert.default.methods")) {
            if (defaultEndpointsIndex != -1) {
                val indent =
                    if (psiFile != null) calculateIndent(psiFile, document, defaultEndpointsIndex)
                    else ""
                val textToInsert = buildString {
                    append('\n')
                    append('\n')
                    append(indent)
                    append(MAP_DEFAULT_ENDPOINTS)
                }
                document.insertString(defaultEndpointsIndex + 1, textToInsert)
            }
            if (serviceDefaultsIndex != -1) {
                val indent =
                    if (psiFile != null) calculateIndent(psiFile, document, serviceDefaultsIndex)
                    else ""
                val textToInsert = buildString {
                    append('\n')
                    append('\n')
                    append(indent)
                    append(ADD_SERVICE_DEFAULTS_METHOD)
                }
                document.insertString(serviceDefaultsIndex + 1, textToInsert)
            }
            if (isMauiProject && !text.contains(USING_MICROSOFT_EXTENSION_HOSTING)) {
                val textToInsert = buildString {
                    append(USING_MICROSOFT_EXTENSION_HOSTING)
                    append('\n')
                }
                document.insertString(0, textToInsert)
            }
            return@writeCommandAction true
        }
    }

    private fun findSemicolonIndexAfterMethod(text: String, methodToInsert: String, insertAfterMethod: String): Int {
        if (text.contains(methodToInsert)) return -1

        val methodIndex = text.indexOf(insertAfterMethod)
        if (methodIndex == -1) {
            LOG.info("Unable to find $insertAfterMethod method in the `Porgram.cs` file")
            return -1
        }

        return text.indexOf(';', methodIndex)
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
