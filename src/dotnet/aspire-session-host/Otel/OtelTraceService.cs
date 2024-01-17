using AspireSessionHost.Sessions;
using Google.Protobuf.Collections;
using Grpc.Core;
using OpenTelemetry.Proto.Collector.Trace.V1;
using OpenTelemetry.Proto.Common.V1;
using OpenTelemetry.Proto.Trace.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelTraceService(
    TraceService.TraceServiceClient client,
    SessionNodeService nodeService,
    ILogger<OtelTraceService> logger
) : TraceService.TraceServiceBase
{
    public override async Task<ExportTraceServiceResponse> Export(
        ExportTraceServiceRequest request,
        ServerCallContext context)
    {
        foreach (var resourceSpan in request.ResourceSpans)
        {
            var serviceName = resourceSpan.Resource.GetServiceName();
            if (serviceName is null) continue;
            ReportScopeSpans(serviceName, resourceSpan.ScopeSpans);
        }

        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }

    private void ReportScopeSpans(string serviceName, RepeatedField<ScopeSpans> scopeSpans)
    {
        foreach (var scopeSpan in scopeSpans)
        {
            if (scopeSpan.Scope.Name is null) continue;
            ReportSpans(serviceName, scopeSpan.Scope.Name, scopeSpan.Spans);
        }
    }

    private void ReportSpans(string serviceName, string scopeName, RepeatedField<Span> spans)
    {
        foreach (var span in spans)
        {
            var spanId = span.SpanId.ToHexString();
            var parentSpanId = span.ParentSpanId.ToHexString();

            logger.LogTrace("Span received ({otelSpanName}, {otelSpanId}, {otelParentSpanIdId})",
                span.Name, spanId, parentSpanId);

            var nodeId = scopeName switch
            {
                "Microsoft.AspNetCore" => GetAspNetCoreNodeId(serviceName, span.Attributes),
                "System.Net.Http" => GetNetHttpNodeId(serviceName, span.Attributes),
                _ => $"{serviceName}-{span.Name}"
            };

            var attributes = new Dictionary<string, string>(span.Attributes.Count);
            foreach (var attribute in span.Attributes)
            {
                attributes[attribute.Key] = attribute.Value.StringValue;
            }

            nodeService.ReportTrace(nodeId, span.Name, serviceName, spanId, parentSpanId, attributes);
        }
    }

    private string GetAspNetCoreNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? method = null;
        string? target = null;

        foreach (var attribute in attributes)
        {
            if (attribute.Key == "http.method") method = attribute.Value.StringValue;
            if (attribute.Key == "http.target") target = attribute.Value.StringValue;
        }

        logger.LogTrace("Span Microsoft.AspNetCore attributes {otelSpanHttpMethod} and {otelSpanHttpTarget}",
            method, target);

        return $"{service}-{method}-{target}";
    }

    private string GetNetHttpNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? method = null;
        string? url = null;

        foreach (var attribute in attributes)
        {
            if (attribute.Key == "http.method") method = attribute.Value.StringValue;
            if (attribute.Key == "http.url") url = attribute.Value.StringValue;
        }

        logger.LogTrace("Span System.Net.Http attributes {otelSpanHttpMethod} and {otelSpanHttpUrl}",
            method, url);

        return $"{service}-{method}-{url}";
    }
}