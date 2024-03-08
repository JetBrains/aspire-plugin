using AspireSessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace AspireSessionHost.Sessions;

internal sealed class SessionService(Connection connection, ILogger<SessionService> logger)
{
    internal async Task<SessionCreationResult?> Create(Session session)
    {
        if (!File.Exists(session.ProjectPath))
        {
            return null;
        }

        var launchConfiguration = session.LaunchConfigurations
            .FirstOrDefault(it => string.Equals(it.Type, "project", StringComparison.InvariantCultureIgnoreCase));
        if (launchConfiguration is null) return null;

        var id = Guid.NewGuid();
        var stringId = id.ToString();
        var envs = session.Env
            ?.Where(it => it.Value is not null)
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            ?.ToArray();
        var sessionModel = new SessionModel(
            stringId,
            launchConfiguration.ProjectPath,
            launchConfiguration.Mode == Mode.Debug,
            session.Args,
            envs
        );
        logger.LogInformation("Starting a new session {session}", sessionModel);

        return await connection.DoWithModel(model => model.CreateSession.Sync(sessionModel));
    }

    internal async Task<bool> Delete(string id)
    {
        logger.LogInformation("Deleting the new session {sessionId}", id);

        return await connection.DoWithModel(model => model.DeleteSession.Sync(id));
    }
}