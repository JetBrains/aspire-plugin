using System.Threading.Channels;
using JetBrains.Lifetimes;

// ReSharper disable ReplaceAsyncWithTaskReturn

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

internal sealed class SessionEventService(Connection connection) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly Channel<ISessionEvent> _channel = Channel.CreateBounded<ISessionEvent>(
        new BoundedChannelOptions(100)
        {
            SingleReader = true,
            SingleWriter = true,
            FullMode = BoundedChannelFullMode.DropOldest
        });

    internal async Task Initialize()
    {
        await connection.DoWithModel(model =>
        {
            model.ProcessStarted.Advise(_lifetimeDef.Lifetime,
                it =>
                {
                    _channel.Writer.TryWrite(new ProcessStartedEvent(it.Id, "processRestarted", it.Pid));
                });

            model.LogReceived.Advise(_lifetimeDef.Lifetime,
                it =>
                {
                    _channel.Writer.TryWrite(new LogReceivedEvent(it.Id, "serviceLogs", it.IsStdErr, it.Message));
                });

            model.ProcessTerminated.Advise(_lifetimeDef.Lifetime,
                it =>
                {
                    _channel.Writer.TryWrite(new ProcessTerminatedEvent(it.Id, "sessionTerminated", it.ExitCode));
                });
        });
    }

    internal ChannelReader<ISessionEvent> Reader => _channel.Reader;

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}