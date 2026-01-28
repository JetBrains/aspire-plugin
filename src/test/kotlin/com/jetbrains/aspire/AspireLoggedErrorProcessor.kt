package com.jetbrains.aspire

import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors
import kotlin.text.contains

val aspireLoggedErrorProcessor: RiderLoggedErrorProcessor = RiderLoggedErrorProcessor(
    RiderKnownLogErrors + KnownLogErrors(
        "DebuggerMatchingHandlerNotFound" to { it.contains("Haven't found matching handler for") },
        "RiderHotReloadExecutionListenerDisposable" to { it.contains("com.jetbrains.rider.debugger.dialogs.RiderHotReloadExecutionListener.processStarted") },
    ))