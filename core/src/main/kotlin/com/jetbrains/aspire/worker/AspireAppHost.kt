package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import java.nio.file.Path

class AspireAppHost(
    val mainFilePath: Path,
    private val project: Project
) {
}