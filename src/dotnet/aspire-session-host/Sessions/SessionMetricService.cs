using System.Collections.Concurrent;
using System.Threading.Channels;
using AspireSessionHost.Generated;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;

#pragma warning disable CS4014 // Because this call is not awaited, execution of the current method continues before the call is completed

namespace AspireSessionHost.Sessions;

internal sealed class SessionMetricService(Connection connection) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ConcurrentDictionary<string, SessionModel> _sessions = new();

    private readonly Channel<SessionMetric> _channel = Channel.CreateBounded<SessionMetric>(
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
            // model.Sessions.View(_lifetimeDef.Lifetime, (lifetime, _, session) =>
            // {
            //     var serviceName = session.TelemetryServiceName;
            //     if (serviceName != null)
            //     {
            //         _sessions.AddLifetimed(lifetime, new(serviceName, session));
            //     }
            // });
        });

        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await ConsumeMetrics());
    }

    internal void ReportMetric(SessionMetric metric)
    {
        _channel.Writer.TryWrite(metric);
    }

    private async Task ConsumeMetrics()
    {
        try
        {
            await foreach (var metric in _channel.Reader.ReadAllAsync(Lifetime.AsyncLocal.Value))
            {
                ConsumeMetric(metric);
            }
        }
        catch (OperationCanceledException)
        {
            //do nothing
        }
    }

    private void ConsumeMetric(SessionMetric metric)
    {
        if (!_sessions.TryGetValue(metric.ServiceName, out var session))
        {
            return;
        }

        var key = new MetricKey(metric.ScopeName, metric.MetricName);
        var value = new MetricValue(
            metric.ServiceName,
            metric.ScopeName,
            metric.MetricName,
            metric.Description,
            metric.Unit,
            metric.Value,
            metric.Timestamp
        );
        // session.Metrics[key] = value;
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}