using AspireSessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace AspireSessionHost.Sessions;

internal sealed class SessionService(Connection connection)
{
    private const string TelemetryServiceName = "OTEL_SERVICE_NAME";

    internal async Task<Guid?> Create(Session session)
    {
        var id = Guid.NewGuid();
        var stringId = id.ToString();
        var serviceName = session.Env?.FirstOrDefault(it => it.Name == TelemetryServiceName);
        var sessionModel = new SessionModel(
            stringId,
            session.ProjectPath,
            session.Debug,
            session.Env?.Select(it => new EnvironmentVariableModel(it.Name, it.Value)).ToArray(),
            session.Args,
            serviceName?.Value
        );

        var result = await connection.DoWithModel(model => model.Sessions.TryAdd(stringId, sessionModel));

        return result ? id : null;
    }

    internal async Task<bool> Delete(Guid id)
    {
        return await connection.DoWithModel(model => model.Sessions.Remove(id.ToString()));
    }
}