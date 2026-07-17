package com.jetbrains.aspire

import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors

val aspireLoggedErrorProcessor: RiderLoggedErrorProcessor = RiderLoggedErrorProcessor(
    RiderKnownLogErrors + KnownLogErrors(
        "WriteLockModalProgress" to { it.contains("This thread holds write lock while trying to invoke a modal progress") },
        "AwtEventsInsideWriteAction" to { it.contains("AWT events are not allowed inside write action") &&  it.contains("com.intellij.openapi.application.impl.NonBlockingFlushQueue")},
        "WorkspaceModelEDT" to { it.contains("com.jetbrains.rider.projectView.workspace.impl.WorkspaceModelUpdater") },
        "SynchronousRefreshUnderReadLock" to { it.contains("Do not perform a synchronous refresh under read lock") },
        "DefaultStacktraceFilter" to { it.contains("Error while applying com.jetbrains.cidr.execution.DefaultStacktraceFilter") },
    )
)