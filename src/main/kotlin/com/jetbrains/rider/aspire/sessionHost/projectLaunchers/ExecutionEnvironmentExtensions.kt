package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor

fun ExecutionEnvironment.setProgramCallbacks(hotReloadCallback: ProgramRunner.Callback? = null) {
    callback = object : ProgramRunner.Callback {
        override fun processStarted(runContentDescriptor: RunContentDescriptor?) {
            runContentDescriptor?.apply {
                isActivateToolWindowWhenAdded = false
                isAutoFocusContent = false
            }

            hotReloadCallback?.processStarted(runContentDescriptor)
        }

        override fun processNotStarted(error: Throwable?) {
            hotReloadCallback?.processNotStarted(error)
        }
    }
}