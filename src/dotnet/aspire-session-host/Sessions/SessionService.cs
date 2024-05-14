using System.Diagnostics;
using AspireSessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace AspireSessionHost.Sessions;

internal sealed class SessionService(Connection connection, ILogger<SessionService> logger)
{
    internal async Task<SessionCreationResult?> Create(Session session)
    {
        var envs = session.Env
            ?.Where(it => it.Value is not null)
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            ?.ToArray();

        SessionModel? sessionModel;
        if (session.ProjectPath != null)
        {
            sessionModel = CreateFromProject(
                session.ProjectPath,
                session.Debug,
                session.LaunchProfile,
                session.DisableLaunchProfile,
                envs,
                session.Args
            );
        }
        else if (session.LaunchConfigurations != null)
        {
            if (session.LaunchConfigurations.Length != 1) return null;
            var launchConfig = session.LaunchConfigurations.Single();
            sessionModel = CreateFromLaunchConfiguration(
                launchConfig,
                envs,
                session.Args
            );
        }
        else
        {
            return null;
        }

        if (sessionModel is null)
        {
            return null;
        }

        logger.LogInformation("Starting a new session {session}", sessionModel);

        return await connection.DoWithModel(model => model.CreateSession.Sync(sessionModel));
    }

    private SessionModel? CreateFromProject(
        string projectPath,
        bool? debug,
        string? launchProfile,
        bool? disableLaunchProfile,
        SessionEnvironmentVariable[]? envs,
        string[]? args)
    {
        if (!File.Exists(projectPath))
        {
            return null;
        }

        return new SessionModel(
            projectPath,
            debug ?? false,
            launchProfile is not null ? [launchProfile] : [],
            disableLaunchProfile ?? false,
            args,
            envs
        );
    }

    private SessionModel? CreateFromLaunchConfiguration(
        LaunchConfiguration launchConfiguration,
        SessionEnvironmentVariable[]? envs,
        string[]? args)
    {
        if (!File.Exists(launchConfiguration.ProjectPath))
        {
            return null;
        }

        if (!string.Equals(launchConfiguration.Type, "project", StringComparison.InvariantCultureIgnoreCase))
        {
            return null;
        }

        return new SessionModel(
            launchConfiguration.ProjectPath,
            launchConfiguration.Mode == Mode.Debug,
            launchConfiguration.LaunchProfile,
            launchConfiguration.DisableLaunchProfile == true,
            args,
            envs
        );
    }

    internal async Task<bool> Delete(string id)
    {
        logger.LogInformation("Deleting the new session {sessionId}", id);

        return await connection.DoWithModel(model => model.DeleteSession.Sync(id));
    }
}