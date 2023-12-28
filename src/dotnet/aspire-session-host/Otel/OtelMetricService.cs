using AspireSessionHost.Sessions;
using Google.Protobuf.Collections;
using Grpc.Core;
using OpenTelemetry.Proto.Collector.Metrics.V1;
using OpenTelemetry.Proto.Metrics.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelMetricService(MetricsService.MetricsServiceClient client, SessionMetricService metricService)
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
            ReportScopeMetrics(serviceName, resourceMetric.ScopeMetrics);
        }

        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }

    private void ReportScopeMetrics(string serviceName, RepeatedField<ScopeMetrics> scopeMetrics)
    {
        foreach (var scopeMetric in scopeMetrics)
        {
            if (scopeMetric.Scope.Name is null) continue;
            ReportMetrics(serviceName, scopeMetric.Scope.Name, scopeMetric.Metrics);
        }
    }

    private void ReportMetrics(string serviceName, string scopeName, RepeatedField<Metric> metrics)
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
                        ReportValue(serviceName, scopeName, name, description, unit, dataPoint);
                    }

                    break;
                case Metric.DataOneofCase.Sum:
                    foreach (var dataPoint in metric.Sum.DataPoints)
                    {
                        ReportValue(serviceName, scopeName, name, description, unit, dataPoint);
                    }

                    break;
                case Metric.DataOneofCase.Histogram:
                    break;
            }
        }
    }

    private void ReportValue(
        string serviceName,
        string scopeName,
        string metricName,
        string description,
        string unit,
        NumberDataPoint dataPoint)
    {
        var timestamp = (long)(dataPoint.TimeUnixNano / 1_000_000_000);
        var sessionMetric = dataPoint.ValueCase switch
        {
            NumberDataPoint.ValueOneofCase.AsDouble => new SessionMetric(
                serviceName,
                scopeName,
                metricName,
                description,
                unit,
                dataPoint.AsDouble,
                timestamp
            ),
            NumberDataPoint.ValueOneofCase.AsInt => new SessionMetric(
                serviceName,
                scopeName,
                metricName,
                description,
                unit,
                dataPoint.AsInt,
                timestamp
            ),
            _ => null
        };

        if (sessionMetric is null) return;

        metricService.ReportMetric(sessionMetric);
    }
}