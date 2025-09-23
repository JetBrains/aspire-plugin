package com.jetbrains.rider.aspire.orchestration

import java.nio.file.Path

data class GeneratedAspireProjects(
    val appHostProjectPath: Path?,
    val serviceDefaultsProjectPath: Path?,
    val mauiServiceDefaultsProjectPath: Path?,
)