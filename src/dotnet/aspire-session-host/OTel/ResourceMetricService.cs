using System.Collections.Concurrent;
using System.Threading.Channels;
using AspireSessionHost.Generated;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;

#pragma warning disable CS4014 // Because this call is not awaited, execution of the current method continues before the call is completed

namespace AspireSessionHost.OTel;

internal sealed class ResourceMetricService(Connection connection) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ConcurrentDictionary<string, ResourceWrapper> _resources = new();

    private readonly Channel<OTelMetric> _channel = Channel.CreateBounded<OTelMetric>(
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
            model.Resources.View(_lifetimeDef.Lifetime, (lifetime, _, resource) =>
            {
                if (!SaveToResourceCollection(resource.Model.Value, resource, lifetime))
                {
                    resource.Model.Advise(lifetime, it =>
                    {
                        SaveToResourceCollection(it, resource, lifetime);
                    });
                }
            });
        });

        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await ConsumeMetrics());
    }

    private bool SaveToResourceCollection(ResourceModel model, ResourceWrapper resource, Lifetime lifetime)
    {
        var serviceName = model.Environment.FirstOrDefault(it => it.Key == "OTEL_SERVICE_NAME");
        if (serviceName?.Value is null) return false;
        if (_resources.ContainsKey(serviceName.Value)) return false;

        _resources.AddLifetimed(lifetime, new(serviceName.Value, resource));
        return true;
    }

    internal void ReportMetric(OTelMetric metric)
    {
        _channel.Writer.TryWrite(metric);
    }

    private async Task ConsumeMetrics()
    {
        try
        {
            await foreach (var metric in _channel.Reader.ReadAllAsync(Lifetime.AsyncLocal.Value))
            {
                await ConsumeMetric(metric);
            }
        }
        catch (OperationCanceledException)
        {
            //do nothing
        }
    }

    private async Task ConsumeMetric(OTelMetric metric)
    {
        if (!_resources.TryGetValue(metric.ServiceName, out var resource))
        {
            return;
        }

        var value = new ResourceMetric(
            metric.ServiceName,
            metric.ScopeName,
            metric.MetricName,
            metric.Description,
            metric.Unit,
            metric.Value,
            metric.Timestamp
        );

        await connection.DoWithModel(_ => resource.MetricReceived(value));
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}