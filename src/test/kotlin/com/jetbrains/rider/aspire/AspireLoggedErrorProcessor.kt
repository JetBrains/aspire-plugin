package com.jetbrains.rider.aspire

import com.jetbrains.rider.test.logging.RiderLoggedErrorProcessor
import com.jetbrains.rider.test.logging.knownErrors.KnownLogErrors
import com.jetbrains.rider.test.logging.knownErrors.RiderKnownLogErrors
import kotlin.text.contains

val aspireLoggedErrorProcessor: RiderLoggedErrorProcessor = RiderLoggedErrorProcessor(
    RiderKnownLogErrors + KnownLogErrors(
        "JSvgDocumentFactoryKt" to { it.contains("com.intellij.ui.svg.JSvgDocumentFactoryKt tried to access method 'void com.github.weisj.jsvg.parser.ParsedElement.<init>") }
    ))