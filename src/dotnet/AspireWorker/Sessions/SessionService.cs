using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal interface ISessionService
{
    Task SubscribeToSessionEvents();
    ChannelReader<ISessionEvent> SessionEventReader { get; }
    Task<(string? sessionId, Errors.IError? error)> CreateSession(string aspireHostId, Session session);
    Task<(string? sessionId, Errors.IError? error)> DeleteSession(string aspireHostId, string sessionId);
}

internal sealed class SessionService(IRdConnectionWrapper connectionWrapper, ILoggerFactory loggerFactory)
    : IDisposable, ISessionService
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ILogger _logger = loggerFactory.CreateLogger<SessionService>();

    private readonly Channel<ISessionEvent> _sessionEventChannel = Channel.CreateUnbounded<ISessionEvent>();
    public ChannelReader<ISessionEvent> SessionEventReader => _sessionEventChannel.Reader;

    public async Task SubscribeToSessionEvents()
    {
        var sessionEventWatcherLogger = loggerFactory.CreateLogger<SessionEventWatcher>();
        var sessionEventWatcher = new SessionEventWatcher(
            connectionWrapper,
            _sessionEventChannel.Writer,
            sessionEventWatcherLogger,
            _lifetimeDef.Lifetime.CreateNested().Lifetime);
        await sessionEventWatcher.WatchSessionEvents();
    }

    public async Task<(string? sessionId, Errors.IError? error)> CreateSession(string aspireHostId, Session session)
    {
        var projectLaunchConfiguration = session.LaunchConfigurations
            .OfType<ProjectLaunchConfiguration>()
            .SingleOrDefault();
        if (projectLaunchConfiguration != null)
        {
            return await CreateProjectSession(session, projectLaunchConfiguration);
        }

        var pythonLaunchConfiguration = session.LaunchConfigurations
            .OfType<PythonLaunchConfiguration>()
            .SingleOrDefault();
        if (pythonLaunchConfiguration != null)
        {
            return await CreatePythonSession(session, pythonLaunchConfiguration);
        }

        _logger.UnableToFindAnySupportedLaunchConfiguration();
        return (null, Errors.UnableToFindSupportedLaunchConfiguration);
    }

    private async Task<(string? sessionId, Errors.IError? error)> CreateProjectSession(
        Session session,
        ProjectLaunchConfiguration launchConfiguration)
    {
        var envs = MapEnvironmentVariables(session);

        var request = new CreateProjectSessionRequest(
            launchConfiguration.ProjectPath,
            launchConfiguration.LaunchProfile,
            launchConfiguration.DisableLaunchProfile == true,
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
        Session session,
        PythonLaunchConfiguration launchConfiguration)
    {
        var envs = MapEnvironmentVariables(session);

        var request = new CreatePythonSessionRequest(
            launchConfiguration.ProgramPath,
            launchConfiguration.InterpreterPath,
            launchConfiguration.Module,
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
        var request = new DeleteSessionRequest(sessionId);

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