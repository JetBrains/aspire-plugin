package com.jetbrains.aspire

import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors

val aspireLoggedErrorProcessor: RiderLoggedErrorProcessor = RiderLoggedErrorProcessor(
    RiderKnownLogErrors + KnownLogErrors(
        "WorkspaceModelEDT" to { it.contains("com.jetbrains.rider.projectView.workspace.impl.WorkspaceModelUpdater") },
        "SynchronousRefreshUnderReadLock" to { it.contains("Do not perform a synchronous refresh under read lock") },
        "DefaultStacktraceFilter" to { it.contains("Error while applying com.jetbrains.cidr.execution.DefaultStacktraceFilter") },
        "DebuggerRaisedTargetHitBreakpointEvent" to { it.contains("Exception in debugger process: Debugger raised TargetHitBreakpoint event, but no corresponding breakpoint model found for breakpoint ClrTypeName: System.Diagnostics.Debugger") },
    )
)