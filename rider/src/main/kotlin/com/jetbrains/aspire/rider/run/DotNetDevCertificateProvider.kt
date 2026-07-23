package com.jetbrains.aspire.rider.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.extensions.DevCertificateCheckResult
import com.jetbrains.aspire.worker.dcp.AspireSessionTlsConfig

internal class DotNetDevCertificateProvider : DevCertificateProvider {
    override suspend fun checkDevCertificate(useBundledRuntime: Boolean, project: Project): DevCertificateCheckResult =
        checkDevCertificate(useBundledRuntime, project, showNotification = false)

    override suspend fun exportCertificate(useBundledRuntime: Boolean, project: Project): String? =
        exportDevCertificate(useBundledRuntime, project)

    override suspend fun exportTlsConfig(useBundledRuntime: Boolean, project: Project): AspireSessionTlsConfig? =
        exportDevCertificateTlsConfig(useBundledRuntime, project)
}
