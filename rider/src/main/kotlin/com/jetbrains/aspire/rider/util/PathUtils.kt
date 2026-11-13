package com.jetbrains.aspire.rider.util

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

internal val Path.pathWithoutExeOrDllExtension: Path
    get() = when (extension.lowercase()) {
        "exe", "dll" -> this.resolveSibling(this.nameWithoutExtension)
        else -> this
    }