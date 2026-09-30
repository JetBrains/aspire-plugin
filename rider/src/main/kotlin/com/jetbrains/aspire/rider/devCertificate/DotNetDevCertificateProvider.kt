package com.jetbrains.aspire.rider.devCertificate

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.certificates.DevCertificateKeyMaterial
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.certificates.DevCertificateCheckResult

internal class DotNetDevCertificateProvider : DevCertificateProvider {
    override suspend fun checkDevCertificate(
        useBundledRuntime: Boolean,
        project: Project,
        showNotification: Boolean
    ): DevCertificateCheckResult = DotNetDevCertificateManager
        .getInstance(project)
        .checkDevCertificate(useBundledRuntime, showNotification)

    override suspend fun exportCertificate(
        useBundledRuntime: Boolean,
        project: Project
    ): Result<String> = DotNetDevCertificateManager
        .getInstance(project)
        .exportDevCertificateAndReadFile(useBundledRuntime)

    override suspend fun exportCertificateWithPrivateKey(
        useBundledRuntime: Boolean,
        project: Project
    ): Result<DevCertificateKeyMaterial> = DotNetDevCertificateManager
        .getInstance(project)
        .exportDevCertificateAndLoadToKeyStore(useBundledRuntime)
}
