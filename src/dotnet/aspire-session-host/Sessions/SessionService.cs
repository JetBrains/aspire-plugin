using AspireSessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace AspireSessionHost.Sessions;

internal sealed class SessionService(Connection connection, ILogger<SessionService> logger)
{
    internal async Task<SessionCreationResult?> Create(Session session)
    {
        if (session.LaunchConfigurations.Length != 1)
        {
            return null;
        }

        var launchConfig = session.LaunchConfigurations.Single();
        if (!File.Exists(launchConfig.ProjectPath))
        {
            return null;
        }

        if (!string.Equals(launchConfig.Type, "project", StringComparison.InvariantCultureIgnoreCase))
        {
            return null;
        }

        var envs = session.Env
            ?.Where(it => it.Value is not null)
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            ?.ToArray();
        var sessionModel = new SessionModel(
            launchConfig.ProjectPath,
            launchConfig.Mode == Mode.Debug,
            launchConfig.LaunchProfile,
            launchConfig.DisableLaunchProfile == true,
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