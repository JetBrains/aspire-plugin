@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.orchestration

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
import com.jetbrains.rider.aspire.AspireBundle
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
import kotlin.io.path.nameWithoutExtension

/**
 * Service responsible for modifying files in a solution with .NET Aspire, providing functionality
 * to insert the necessary methods into specific files (`AppHost.cs` and `Program.cs`)
 * to configure Aspire host and project applications correctly.
 */
@Service(Service.Level.PROJECT)
internal class AspireDefaultFileModificationService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireDefaultFileModificationService = project.service()
        private val LOG = logger<AspireDefaultFileModificationService>()

        private const val APP_HOST_FILE = "AppHost.cs"
        private const val PROGRAM_FILE = "Program.cs"
        private const val MAUI_PROGRAM_FILE = "MauiProgram.cs"
        private const val ADD_PROJECT_METHOD = ".AddProject("
        private const val CREATE_BUILDER_METHOD = ".CreateBuilder("
        private const val CREATE_APPLICATION_BUILDER_METHOD = ".CreateApplicationBuilder("
        private const val BUILD_METHOD = ".Build("
        private const val ADD_SERVICE_DEFAULTS_METHOD = "builder.AddServiceDefaults();"
        private const val MAP_DEFAULT_ENDPOINTS = "app.MapDefaultEndpoints();"
    }

    /**
     * Inserts `AddProject` methods into the `AppHost` file of the Aspire host project.
     *
     * @param hostProjectPath a file path to an Aspire host project file
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

            writeCommandAction(project, AspireBundle.message("write.command.insert.into.app.host")) {
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

            val insertionResult = insertAspireDefaultMethodsIntoProgramFile(projectProgramFile, isWebProject)
            methodsWereInserted = methodsWereInserted || insertionResult
        }

        return methodsWereInserted
    }

    private suspend fun insertAspireDefaultMethodsIntoProgramFile(
        programFile: VirtualFile,
        isWebProject: Boolean
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

        writeCommandAction(project, AspireBundle.message("write.command.insert.default.methods")) {
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
            return@writeCommandAction true
        }
    }

    private fun findSemicolonIndexAfterMethod(text: String, methodToInsert: String, insertAfterMethod: String): Int {
        if (text.contains(methodToInsert)) return -1

        val methodIndex = text.indexOf(insertAfterMethod)
        if (methodIndex == -1) {
            LOG.warn("Unable to find $insertAfterMethod method in the `Porgram.cs` file")
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