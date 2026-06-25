package com.jetbrains.aspire

import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors

val aspireLoggedErrorProcessor: RiderLoggedErrorProcessor = RiderLoggedErrorProcessor(
    RiderKnownLogErrors + KnownLogErrors(
        "DebuggerMatchingHandlerNotFound" to { it.contains("Haven't found matching handler for") },
        "HighlighterHasNotBeenRegistered" to { it.contains("Removing highlighter hasn't been registered by markup adapter") },
        "RiderHotReloadExecutionListenerDisposable" to { it.contains("com.jetbrains.rider.debugger.dialogs.RiderHotReloadExecutionListener.processStarted") },
        "DoubleReleaseOfEditor" to { it.contains($$"TraceableDisposable$DisposalException: Double release of editor") },
        "WriteLockModalProgress" to { it.contains("This thread holds write lock while trying to invoke a modal progress") },
        "AwtEventsInsideWriteAction" to { it.contains("AWT events are not allowed inside write action") &&  it.contains("com.intellij.openapi.application.impl.NonBlockingFlushQueue")},
        "WorkspaceModelEDT" to { it.contains("com.jetbrains.rider.projectView.workspace.impl.WorkspaceModelUpdater") },
    )
)