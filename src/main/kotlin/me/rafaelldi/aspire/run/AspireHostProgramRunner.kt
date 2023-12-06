package me.rafaelldi.aspire.run

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.rider.debugger.DotNetProgramRunner

class AspireHostProgramRunner : DotNetProgramRunner() {
    override fun canRun(executorId: String, runConfiguration: RunProfile) = runConfiguration is AspireHostConfiguration
}