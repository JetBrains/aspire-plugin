using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal static class Errors
{
    internal interface IError
    {
        ErrorDetail ErrorDetail { get; }
    }

    internal interface IClientError : IError;

    internal interface IServerError : IError;


    internal sealed record ProtocolVersionIsNotSupportedError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "ProtocolVersionIsNotSupported",
            "The current protocol version is not supported by the plugin. Probably you should update either Aspire or the plugin."
        );
    }

    internal static readonly ProtocolVersionIsNotSupportedError ProtocolVersionIsNotSupported = new();

    internal sealed record UnexpectedError : IServerError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "UnexpectedError",
            "An unexpected error occurred."
        );
    }

    internal static readonly UnexpectedError Unexpected = new();

    internal sealed record AspireHostNotFoundError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "AspireHostNotFound",
            "Unable to find an Aspire host. Please make sure that Aspire is running."
        );
    }

    internal static readonly AspireHostNotFoundError AspireHostNotFound = new();

    internal sealed record AspireSessionNotFoundError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "AspireSessionNotFound",
            "Unable to find an Aspire session. Please make sure that the session exists."
        );
    }

    internal static readonly AspireSessionNotFoundError AspireSessionNotFound = new();

    internal sealed record UnableToFindSupportedLaunchConfigurationError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "UnableToFindSupportedLaunchConfiguration",
            "Unable to find any supported launch configuration."
        );
    }

    internal static readonly UnableToFindSupportedLaunchConfigurationError UnableToFindSupportedLaunchConfiguration = new();

    internal sealed record UnsupportedLaunchConfigurationTypeError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "UnsupportedLaunchConfigurationType",
            "The provided launch configuration type is not supported."
        );
    }

    internal static readonly UnsupportedLaunchConfigurationTypeError UnsupportedLaunchConfigurationType = new();

    internal sealed record DotNetProjectNotFoundError : IClientError
    {
        public ErrorDetail ErrorDetail { get; } = new(
            "DotNetProjectNotFound",
            "Unable to find a .NET project. Please make sure that the project exists."
        );
    }

    internal static readonly DotNetProjectNotFoundError DotNetProjectNotFound = new();
}

internal static class ErrorExtensions
{
    internal static ErrorResponse ToResponse(this Errors.IError error) => new(error.ErrorDetail);

    internal static Errors.IError ToError(this ErrorCode code) => code switch
    {
        ErrorCode.UnsupportedLaunchConfigurationType => Errors.UnsupportedLaunchConfigurationType,
        ErrorCode.AspireSessionNotFound => Errors.AspireSessionNotFound,
        ErrorCode.DotNetProjectNotFound => Errors.DotNetProjectNotFound,
        _ => Errors.Unexpected
    };
}