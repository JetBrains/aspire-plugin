package com.jetbrains.aspire.rider.debugger

import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.xdebugger.attach.LocalAttachHost
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.jetbrains.aspire.rider.AspireRiderBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A service responsible for attaching a debugger to a specific process.
 */
@Service(Service.Level.PROJECT)
internal class AttachDebuggerService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AttachDebuggerService = project.service()
    }

    @Suppress("UnstableApiUsage")
    suspend fun attach(pid: Int) = withContext(Dispatchers.Default) {
        withBackgroundProgress(project, AspireRiderBundle.message("progress.attach.debugger.to.resource")) {
            val eelProcessInfo = localEel.exec.processManagement.processInfo(pid.toLong())
                ?: return@withBackgroundProgress
            val arguments = eelProcessInfo.arguments.await()
            val executable = eelProcessInfo.executable
            val processInfo = ProcessInfo(
                pid,
                executable?.let { ParametersListUtil.join(listOf(it) + arguments) }.orEmpty(),
                executable?.let { PathUtil.getFileName(it) }.orEmpty(),
                ParametersListUtil.join(arguments),
                executable,
            )
            val attachHost = LocalAttachHost.INSTANCE
            val dataHolder = UserDataHolderBase()
            val debugger = XAttachDebuggerProvider.EP.extensionList
                .filter { it.isAttachHostApplicable(attachHost) }
                .flatMap { it.getAvailableDebuggers(project, attachHost, processInfo, dataHolder) }
                .singleOrNull { it.debuggerDisplayName == ".NET Debugger" }
                ?: return@withBackgroundProgress

            debugger.attachDebugSession(project, attachHost, processInfo)
        }
    }
}
