package com.jetbrains.aspire.python.sessions

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugProcess

class PythonSessionDebugProgramRunner : GenericProgramRunner<RunnerSettings>() {
    companion object {
        const val ID = "aspire.python.session.debug.runner"
    }

    override fun getRunnerId(): String = ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is PythonSessionDebugProfile

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val profile = environment.runProfile as? PythonSessionDebugProfile ?: return null
        val executionResult = state.execute(environment.executor, this) ?: return null

        val session = XDebuggerManager.getInstance(environment.project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                return PyDebugProcess(
                    session,
                    profile.serverSocket,
                    executionResult.executionConsole,
                    executionResult.processHandler,
                    false
                )
            }
        })

        return session.runContentDescriptor
    }
}
