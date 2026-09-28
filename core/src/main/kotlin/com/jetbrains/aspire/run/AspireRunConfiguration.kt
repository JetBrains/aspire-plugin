package com.jetbrains.aspire.run

import com.intellij.execution.configurations.RunProfile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AspireRunConfiguration : RunProfile {
    val appHostFilePath: Path
}
