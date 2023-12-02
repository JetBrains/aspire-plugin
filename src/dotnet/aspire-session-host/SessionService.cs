using System.Collections.Concurrent;
using AspireSessionHost.Generated;
using JetBrains.Lifetimes;

namespace AspireSessionHost;

internal class SessionService(Connection connection) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ConcurrentDictionary<Guid, LifetimeDefinition> _sessions = new();

    internal async Task<Guid> Create(Session session)
    {
        var sessionModel = new SessionModel(
            session.ProjectPath,
            session.Debug,
            session.Env?.Select(it => new EnvironmentVariableModel(it.Name, it.Value)).ToArray(),
            session.Args
        );

        var id = Guid.NewGuid();
        var sessionLifetime = _lifetimeDef.Lifetime.CreateNested();
        _sessions.TryAdd(id, sessionLifetime);

        await connection.DoWithModel(model =>
        {
            sessionLifetime.Lifetime.Bracket(
                () =>
                {
                    model.Sessions.Add(id.ToString(), sessionModel);
                },
                () =>
                {
                    model.Sessions.Remove(id.ToString());
                }
            );
        });

        return id;
    }

    internal bool Delete(Guid id)
    {
        if (!_sessions.TryRemove(id, out var sessionLifetime))
        {
            return false;
        }

        sessionLifetime.Terminate();

        return true;
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}