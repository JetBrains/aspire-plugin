package com.jetbrains.aspire.sessions

import java.nio.file.Path

interface SessionProfile {
    val sessionId: String
    val projectPath: Path
    val aspireHostProjectPath: Path?
    val isDebugMode: Boolean
}