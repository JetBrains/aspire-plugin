package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import java.nio.file.Path

internal class AspireAppHost(
    val hostProjectPath: Path,
    private val project: Project
) {
}