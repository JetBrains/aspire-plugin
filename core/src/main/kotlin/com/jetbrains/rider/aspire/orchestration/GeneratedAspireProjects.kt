package com.jetbrains.rider.aspire.orchestration

import java.nio.file.Path

internal data class GeneratedAspireProjects(
    val appHostProjectPath: Path?,
    val serviceDefaultsProjectPath: Path?,
    val mauiServiceDefaultsProjectPath: Path?,
)