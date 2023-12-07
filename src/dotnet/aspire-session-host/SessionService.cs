using AspireSessionHost.Generated;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace AspireSessionHost;

internal class SessionService(Connection connection)
{
    internal async Task<Guid?> Create(Session session)
    {
        var id = Guid.NewGuid();
        var sessionModel = new SessionModel(
            session.ProjectPath,
            session.Debug,
            session.Env?.Select(it => new EnvironmentVariableModel(it.Name, it.Value)).ToArray(),
            session.Args
        );

        var result = await connection.DoWithModel(model => model.Sessions.TryAdd(id.ToString(), sessionModel));

        return result ? id : null;
    }

    internal async Task<bool> Delete(Guid id)
    {
       return await connection.DoWithModel(model => model.Sessions.Remove(id.ToString()));
    }
}