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

            string nodeId;
            Dictionary<string, string> attributes;
            switch (scopeName)
            {
                case "Microsoft.AspNetCore":
                    var (aspNetMethod, aspNetTarget) = GetAspNetCoreMethodAndTarget(span.Attributes);
                    nodeId = $"{aspNetMethod}-{aspNetTarget}";
                    attributes = new Dictionary<string, string>
                    {
                        { "http.method", aspNetMethod ?? "" },
                        { "http.target", aspNetTarget ?? "" }
                    };
                    break;
                case "System.Net.Http":
                    var (httpMethod, httpUrl) = GetNetHttpMethodAndUrl(span.Attributes);
                    nodeId = $"{httpMethod}-{httpUrl}";
                    attributes = new Dictionary<string, string>
                    {
                        { "http.method", httpMethod ?? "" },
                        { "http.url", httpUrl ?? "" }
                    };
                    break;
                default:
                    nodeId = $"{serviceName}-{span.Name}";
                    attributes = new Dictionary<string, string>();
                    break;
            }

            nodeService.ReportTrace(nodeId, span.Name, serviceName, spanId, parentSpanId, attributes);
        }
    }

    private (string? Method, string? Target) GetAspNetCoreMethodAndTarget(RepeatedField<KeyValue> attributes)
    {
        string? method = null;
        string? target = null;

        foreach (var attribute in attributes)
        {
            if (attribute.Key == "http.method") method = attribute.Value.StringValue;
            if (attribute.Key == "http.target") target = attribute.Value.StringValue;
        }

        logger.LogTrace("Span Microsoft.AspNetCore attributes {otelSpanHttpMethod} and {otelSpanHttpTarget}", method, target);

        return (method, target);
    }

    private (string? Method, string? Url) GetNetHttpMethodAndUrl(RepeatedField<KeyValue> attributes)
    {
        string? method = null;
        string? url = null;

        foreach (var attribute in attributes)
        {
            if (attribute.Key == "http.method") method = attribute.Value.StringValue;
            if (attribute.Key == "http.url") url = attribute.Value.StringValue;
        }

        logger.LogTrace("Span System.Net.Http attributes {otelSpanHttpMethod} and {otelSpanHttpUrl}", method, url);

        return (method, url);
    }
}
