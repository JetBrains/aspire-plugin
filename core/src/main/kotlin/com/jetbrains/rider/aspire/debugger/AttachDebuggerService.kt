package com.jetbrains.rider.aspire.debugger

import com.intellij.execution.process.impl.ProcessListUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.xdebugger.attach.LocalAttachHost
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.jetbrains.rider.aspire.AspireBundle

/**
 * A service responsible for attaching a debugger to a specific process.
 */
@Service(Service.Level.PROJECT)
internal class AttachDebuggerService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AttachDebuggerService = project.service()
    }

    suspend fun attach(pid: Int) {
        withBackgroundProgress(project, AspireBundle.message("progress.attach.debugger.to.resource")) {
            val processInfo = ProcessListUtil.getProcessList().firstOrNull { it.pid == pid }
                ?: return@withBackgroundProgress
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