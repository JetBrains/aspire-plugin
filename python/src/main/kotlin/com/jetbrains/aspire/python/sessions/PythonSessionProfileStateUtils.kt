package com.jetbrains.aspire.python.sessions

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.sessions.PythonSessionLaunchConfiguration
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlin.io.path.absolutePathString

internal fun createSyntheticRunConfiguration(
    launchConfiguration: PythonSessionLaunchConfiguration,
    project: Project
): PythonRunConfiguration {
    val conf = PythonConfigurationType.getInstance().factory
        .createTemplateConfiguration(project) as PythonRunConfiguration

    if (launchConfiguration.module != null) {
        conf.scriptName = launchConfiguration.module
        conf.isModuleMode = true
    } else {
        conf.scriptName = launchConfiguration.programPath.absolutePathString()
        conf.isModuleMode = false
    }

    conf.scriptParameters = ParametersListUtil.join(launchConfiguration.args ?: emptyList())
    conf.workingDirectory = launchConfiguration.programPath.parent?.absolutePathString() ?: ""

    val envMap = LinkedHashMap<String, String>()
    launchConfiguration.envs?.forEach { (k, v) -> envMap[k] = v }
    conf.envs = envMap
    conf.isPassParentEnvs = true

    val sdk = resolveSdk(launchConfiguration.interpreterPath, project)
    if (sdk != null) conf.sdk = sdk

    conf.setAddContentRoots(true)
    conf.setAddSourceRoots(true)

    return conf
}

private fun resolveSdk(interpreterPath: String?, project: Project): Sdk? {
    if (interpreterPath != null) {
        PythonSdkUtil.findSdkByPath(interpreterPath)?.let { return it }
    }

    for (module in ModuleManager.getInstance(project).modules) {
        val sdk = PythonSdkUtil.findPythonSdk(module)
        if (sdk != null) return sdk
    }

    return ProjectRootManager.getInstance(project).projectSdk
}
