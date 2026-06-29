package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.generated.ErrorCode


internal enum class DcpErrorKind {
    CLIENT,
    SERVER,
}

internal data class DcpError(
    val kind: DcpErrorKind,
    val detail: ErrorDetail,
)

internal object DcpErrors {
    val ProtocolVersionIsNotSupported = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "ProtocolVersionIsNotSupported",
            "The current protocol version is not supported by the plugin. Probably you should update either Aspire or the plugin.",
        ),
    )

    val AspireAppHostNotFound = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "AspireAppHostNotFound",
            "Unable to find an Aspire AppHost. Please make sure that Aspire is running."
        ),
    )

    val AspireSessionNotFound = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "AspireSessionNotFound",
            "Unable to find an Aspire session. Please make sure that the session exists."
        ),
    )

    val InvalidAspireHostId = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "InvalidAspireHostId",
            "Aspire Host id is not specified",
        ),
    )

    val UnableToFindSupportedLaunchConfiguration = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "UnableToFindSupportedLaunchConfiguration",
            "Unable to find any supported launch configuration.",
        ),
    )

    val UnsupportedLaunchConfigurationType = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "UnsupportedLaunchConfigurationType",
            "The provided launch configuration type is not supported.",
        ),
    )

    val DotNetProjectNotFound = DcpError(
        DcpErrorKind.CLIENT,
        ErrorDetail(
            "DotNetProjectNotFound",
            "Unable to find a .NET project. Please make sure that the project exists."
        ),
    )

    val Unexpected = DcpError(
        DcpErrorKind.SERVER,
        ErrorDetail(
            "UnexpectedError",
            "An unexpected error occurred.",
        ),
    )

    fun fromErrorCode(code: ErrorCode): DcpError = when (code) {
        ErrorCode.AspireAppHostNotFound -> AspireAppHostNotFound
        ErrorCode.UnsupportedLaunchConfigurationType -> UnsupportedLaunchConfigurationType
        ErrorCode.AspireSessionNotFound -> AspireSessionNotFound
        ErrorCode.DotNetProjectNotFound -> DotNetProjectNotFound
        ErrorCode.UnableToFindSupportedLaunchConfiguration -> UnableToFindSupportedLaunchConfiguration
        ErrorCode.Unexpected -> Unexpected
    }
}