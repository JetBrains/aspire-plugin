﻿using JetBrains.Rider.Aspire.SessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

internal sealed class SessionService(Connection connection, ILogger<SessionService> logger)
{
    private readonly ErrorResponse _multipleProjectLaunchConfigurations = new(new ErrorDetail(
        "BadRequest",
        "Only a single launch configuration instance, of type project, can be used as part of a run session request."
    ));

    private readonly ErrorResponse _projectNotFound = new(new ErrorDetail(
        "NotFound",
        "A project file is not found."
    ));

    internal async Task<(SessionCreationResult?, ErrorResponse?)> Create(Session session)
    {
        var launchConfiguration = session.LaunchConfigurations.SingleOrDefault(it =>
            string.Equals(it.Type, "project", StringComparison.InvariantCultureIgnoreCase)
        );
        if (launchConfiguration == null)
        {
            logger.LogWarning("Only a single project launch configuration is supported.");
            return (null, _multipleProjectLaunchConfigurations);
        }

        if (!File.Exists(launchConfiguration.ProjectPath))
        {
            logger.LogWarning("Project file doesn't exist");
            return (null, _projectNotFound);
        }

        var envs = session.Env
            ?.Where(it => it.Value is not null)
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            ?.ToArray();

        var sessionModel = new SessionModel(
            launchConfiguration.ProjectPath,
            launchConfiguration.Mode == Mode.Debug,
            launchConfiguration.LaunchProfile,
            launchConfiguration.DisableLaunchProfile == true,
            session.Args,
            envs
        );

        logger.LogInformation("Starting a new session {session}", sessionModel);

        var result = await connection.DoWithModel(model => model.CreateSession.Sync(sessionModel));
        logger.LogDebug("Session creation result: {sessionCreationResult}", result);

        return (result, null);
    }

    internal async Task<bool> Delete(string id)
    {
        logger.LogInformation("Deleting the session {sessionId}", id);

        var result = await connection.DoWithModel(model => model.DeleteSession.Sync(id));
        logger.LogDebug("Session deletion result: {sessionDeletionResult}", result);

        return result;
    }
}