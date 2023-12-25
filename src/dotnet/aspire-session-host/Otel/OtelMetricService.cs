using AspireSessionHost.Generated;
using Google.Protobuf.Collections;
using Grpc.Core;
using OpenTelemetry.Proto.Collector.Metrics.V1;
using OpenTelemetry.Proto.Metrics.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelMetricService(MetricsService.MetricsServiceClient client, Connection connection)
    : MetricsService.MetricsServiceBase
{
    public override async Task<ExportMetricsServiceResponse> Export(
        ExportMetricsServiceRequest request,
        ServerCallContext context)
    {
        foreach (var resourceMetric in request.ResourceMetrics)
        {
            var serviceName = resourceMetric.Resource.GetServiceName();
            if (serviceName is null) continue;
            await ReportScopeMetrics(serviceName, resourceMetric.ScopeMetrics);
        }

        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }

    private async Task ReportScopeMetrics(string serviceName, RepeatedField<ScopeMetrics> scopeMetrics)
    {
        foreach (var scopeMetric in scopeMetrics)
        {
            if (scopeMetric.Scope.Name is null) continue;
            await ReportMetrics(serviceName, scopeMetric.Scope.Name, scopeMetric.Metrics);
        }
    }

    private async Task ReportMetrics(string serviceName, string scopeName, RepeatedField<Metric> metrics)
    {
        foreach (var metric in metrics)
        {
            var name = metric.Name;
            if (name is null) continue;
            var description = metric.Description;
            var unit = metric.Unit;

            switch (metric.DataCase)
            {
                case Metric.DataOneofCase.Gauge:
                    foreach (var dataPoint in metric.Gauge.DataPoints)
                    {
                        await ReportValue(serviceName, scopeName, name, description, unit, dataPoint);
                    }

                    break;
                case Metric.DataOneofCase.Sum:
                    foreach (var dataPoint in metric.Sum.DataPoints)
                    {
                        await ReportValue(serviceName, scopeName, name, description, unit, dataPoint);
                    }

                    break;
                case Metric.DataOneofCase.Histogram:
                    break;
            }
        }
    }

    private async Task ReportValue(
        string serviceName,
        string scopeName,
        string metricName,
        string description,
        string unit,
        NumberDataPoint dataPoint)
    {
        var timestamp = dataPoint.TimeUnixNano;
        MetricBase? sessionMetric = dataPoint.ValueCase switch
        {
            NumberDataPoint.ValueOneofCase.AsDouble => new MetricDouble(
                dataPoint.AsDouble,
                serviceName,
                scopeName,
                metricName,
                description,
                unit,
                (long)timestamp
            ),
            NumberDataPoint.ValueOneofCase.AsInt => new MetricLong(
                dataPoint.AsInt,
                serviceName,
                scopeName,
                metricName,
                description,
                unit,
                (long)timestamp
            ),
            _ => null
        };

        if (sessionMetric is null) return;

        await connection.DoWithModel(model => model.OtelMetricReceived(sessionMetric));
    }
}