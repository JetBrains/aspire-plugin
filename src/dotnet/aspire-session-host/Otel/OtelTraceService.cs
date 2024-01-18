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
    private const string HttpRequestMethod = "http.request.method";
    private const string HttpMethod = "http.method"; //deprecated
    private const string UrlPath = "url.path";
    private const string UrlQuery = "url.query";
    private const string HttpTarget = "http.target"; //deprecated
    private const string UrlFull = "url.full";
    private const string HttpUrl = "http.url"; //deprecated

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
            var nodeId = scopeName switch
            {
                "Microsoft.AspNetCore" => GetAspNetCoreNodeId(serviceName, span.Attributes),
                "System.Net.Http" => GetNetHttpNodeId(serviceName, span.Attributes),
                _ => $"{serviceName}-{span.Name}"
            };

            if (nodeId == null) continue;

            var spanId = span.SpanId.ToHexString();
            var parentSpanId = span.ParentSpanId.ToHexString();

            logger.LogTrace("Span received ({otelSpanName}, {otelSpanId}, {otelParentSpanIdId})",
                span.Name, spanId, parentSpanId);

            var attributes = new Dictionary<string, string>(span.Attributes.Count);
            foreach (var attribute in span.Attributes)
            {
                attributes[attribute.Key] = attribute.Value.StringValue;
            }

            nodeService.ReportTrace(nodeId, span.Name, serviceName, spanId, parentSpanId, attributes);
        }
    }

    //https://opentelemetry.io/docs/specs/semconv/http/http-spans/
    //https://opentelemetry.io/docs/specs/semconv/attributes-registry/http/
    private string? GetAspNetCoreNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? httpRequestMethod = null;
        string? httpMethod = null;
        string? urlPath = null;
        string? urlQuery = null;
        string? httpTarget = null;

        foreach (var attribute in attributes)
        {
            switch (attribute.Key)
            {
                case HttpRequestMethod:
                    httpRequestMethod = attribute.Value.StringValue;
                    break;
                case HttpMethod:
                    httpMethod = attribute.Value.StringValue;
                    break;
                case UrlPath:
                    urlPath = attribute.Value.StringValue;
                    break;
                case UrlQuery:
                    urlQuery = attribute.Value.StringValue;
                    break;
                case HttpTarget:
                    httpTarget = attribute.Value.StringValue;
                    break;
            }
        }

        var method = !string.IsNullOrEmpty(httpRequestMethod) ? httpRequestMethod : httpMethod;
        var url = string.IsNullOrEmpty(urlQuery) ? urlPath : $"{urlPath}?{urlQuery}";
        var target = !string.IsNullOrEmpty(url) ? url : httpTarget;

        if (string.IsNullOrEmpty(method) || string.IsNullOrEmpty(target))
        {
            logger.LogWarning("Unable to create System.Net.Http span id: {otelSpanMethod}, {otelSpanTarget}", method, target);
            return null;
        }

        var id = $"{service}-{method}-{target}";
        logger.LogTrace("Span Microsoft.AspNetCore id {otelSpanId}", id);

        return id;
    }

    //https://opentelemetry.io/docs/specs/semconv/http/http-spans/
    //https://opentelemetry.io/docs/specs/semconv/attributes-registry/http/
    private string? GetNetHttpNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? httpRequestMethod = null;
        string? httpMethod = null;
        string? urlFull = null;
        string? httpUrl = null;

        foreach (var attribute in attributes)
        {
            switch (attribute.Key)
            {
                case HttpRequestMethod:
                    httpRequestMethod = attribute.Value.StringValue;
                    break;
                case HttpMethod:
                    httpMethod = attribute.Value.StringValue;
                    break;
                case UrlFull:
                    urlFull = attribute.Value.StringValue;
                    break;
                case HttpUrl:
                    httpUrl = attribute.Value.StringValue;
                    break;
            }
        }

        var method = !string.IsNullOrEmpty(httpRequestMethod) ? httpRequestMethod : httpMethod;
        var url = !string.IsNullOrEmpty(urlFull) ? urlFull : httpUrl;

        if (string.IsNullOrEmpty(method) || string.IsNullOrEmpty(url))
        {
            logger.LogWarning("Unable to create System.Net.Http span id: {otelSpanMethod}, {otelSpanUrl}", method, url);
            return null;
        }

        var id = $"{service}-{method}-{url}";
        logger.LogTrace("Span System.Net.Http id {otelSpanId}", id);

        return id;
    }
}