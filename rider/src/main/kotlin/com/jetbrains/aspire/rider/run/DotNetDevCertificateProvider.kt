package com.jetbrains.aspire.rider.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.extensions.DevCertificateCheckResult

internal class DotNetDevCertificateProvider : DevCertificateProvider {
    override suspend fun checkDevCertificate(project: Project): DevCertificateCheckResult =
        checkDevCertificate(project, showNotification = false)

    override suspend fun exportCertificate(project: Project): String? =
        exportDevCertificate(project)
}
