using AspireSessionHost.Generated;

namespace AspireSessionHost;

internal class SessionService(Connection connection)
{
    internal async Task<Guid> Create(Session session)
    {
        var sessionModel = new SessionModel(
            session.ProjectPath,
            session.Debug,
            session.Env?.Select(it => new EnvironmentVariableModel(it.Name, it.Value)).ToArray(),
            session.Args
        );
        var id = Guid.NewGuid();

        await connection.DoWithModel(model => model.Sessions.Add(id.ToString(), sessionModel));

        return id;
    }

    internal async Task<bool> Delete(Guid id)
    {
        var isSuccessful = await connection.DoWithModel(model => model.Sessions.Remove(id.ToString()));

        return isSuccessful;
    }
}