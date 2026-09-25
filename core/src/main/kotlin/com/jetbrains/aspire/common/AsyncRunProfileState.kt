package com.jetbrains.aspire.common

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner

internal interface AsyncRunProfileState : RunProfileState {
    override fun execute(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult? {
        throw UnsupportedOperationException("Use executeSuspending instead")
    }

    suspend fun executeSuspending(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult?
}