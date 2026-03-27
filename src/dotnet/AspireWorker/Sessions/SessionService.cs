using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal interface ISessionService
{
    Task<(string? sessionId, Errors.IError? error)> CreateSession(string aspireHostId, Session session);
    Task<(string? sessionId, Errors.IError? error)> DeleteSession(string aspireHostId, string sessionId);
}

internal sealed class SessionService(IRdConnectionWrapper connectionWrapper, ILoggerFactory loggerFactory)
    : IDisposable, ISessionService
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ILogger _logger = loggerFactory.CreateLogger<SessionService>();

    public async Task<(string? sessionId, Errors.IError? error)> CreateSession(string aspireHostId, Session session)
    {
        var projectLaunchConfiguration = session.LaunchConfigurations
            .OfType<ProjectLaunchConfiguration>()
            .SingleOrDefault();
        if (projectLaunchConfiguration != null)
        {
            return await CreateProjectSession(aspireHostId, session, projectLaunchConfiguration);
        }

        var pythonLaunchConfiguration = session.LaunchConfigurations
            .OfType<PythonLaunchConfiguration>()
            .SingleOrDefault();
        if (pythonLaunchConfiguration != null)
        {
            return await CreatePythonSession(aspireHostId, session, pythonLaunchConfiguration);
        }

        _logger.UnableToFindAnySupportedLaunchConfiguration();
        return (null, Errors.UnableToFindSupportedLaunchConfiguration);
    }

    private async Task<(string? sessionId, Errors.IError? error)> CreateProjectSession(
        string aspireHostId,
        Session session,
        ProjectLaunchConfiguration launchConfiguration)
    {
        var envs = MapEnvironmentVariables(session);

        var request = new CreateProjectSessionRequest(
            launchConfiguration.ProjectPath,
            launchConfiguration.LaunchProfile,
            launchConfiguration.DisableLaunchProfile == true,
            aspireHostId,
            launchConfiguration.Mode == Mode.Debug,
            session.Args,
            envs
        );

        _logger.CreateNewSessionRequestReceived(request.ProjectPath);
        _logger.SessionCreationRequestBuilt(request);

        var result = await connectionWrapper.CreateSession(request);
        _logger.SessionCreationResponseReceived(result);

        var error = result?.Error?.ToError();

        return (result?.SessionId, error);
    }

    private async Task<(string? sessionId, Errors.IError? error)> CreatePythonSession(
        string aspireHostId,
        Session session,
        PythonLaunchConfiguration launchConfiguration)
    {
        var envs = MapEnvironmentVariables(session);

        var request = new CreatePythonSessionRequest(
            launchConfiguration.ProgramPath,
            launchConfiguration.InterpreterPath,
            launchConfiguration.Module,
            aspireHostId,
            launchConfiguration.Mode == Mode.Debug,
            session.Args,
            envs
        );

        _logger.CreateNewSessionRequestReceived(request.ProgramPath);
        _logger.SessionCreationRequestBuilt(request);

        var result = await connectionWrapper.CreateSession(request);
        _logger.SessionCreationResponseReceived(result);

        var error = result?.Error?.ToError();

        return (result?.SessionId, error);
    }

    private static SessionEnvironmentVariable[]? MapEnvironmentVariables(Session session)
    {
        return session.Env
            ?.Where(it => it.Value is not null)
            .Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            .ToArray();
    }

    public async Task<(string? sessionId, Errors.IError? error)> DeleteSession(string aspireHostId, string sessionId)
    {
        var request = new DeleteSessionRequest(aspireHostId, sessionId);

        _logger.DeleteSessionRequestReceived(sessionId);
        _logger.SessionDeletionRequestBuilt(request);

        var result = await connectionWrapper.DeleteSession(request);
        _logger.SessionDeletionResponseReceived(result);

        var error = result?.Error?.ToError();

        return (result?.SessionId, error);
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}