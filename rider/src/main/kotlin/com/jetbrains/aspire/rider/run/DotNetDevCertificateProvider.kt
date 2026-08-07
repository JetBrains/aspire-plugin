package com.jetbrains.aspire.rider.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.certificates.DevCertificateKeyMaterial
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.extensions.DevCertificateCheckResult

internal class DotNetDevCertificateProvider : DevCertificateProvider {
    override suspend fun checkDevCertificate(useBundledRuntime: Boolean, project: Project): DevCertificateCheckResult =
        checkDevCertificate(useBundledRuntime, project, showNotification = false)

    override suspend fun exportCertificate(
        useBundledRuntime: Boolean,
        project: Project
    ): Result<String> = exportDevCertificateAndReadFile(useBundledRuntime, project)

    override suspend fun exportCertificateWithPrivateKey(
        useBundledRuntime: Boolean,
        project: Project
    ): Result<DevCertificateKeyMaterial> = exportDevCertificateAndLoadToKeyStore(useBundledRuntime, project)
}
