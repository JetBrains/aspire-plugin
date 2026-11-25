package com.jetbrains.aspire.secrets

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull

/**
 * Lets the user edit Aspire secrets files (`~/.aspire/secrets/<apphost-id>/<environment>.json`) without a warning,
 * although they are outside the project.
 */
internal class AspireSecretsWritingAccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean {
        val path = file.toNioPathOrNull() ?: return false
        return AspireSecretsEnvironments.isSecretsFile(path)
    }
}
