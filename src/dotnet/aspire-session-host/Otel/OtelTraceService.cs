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
    private const string HttpTarget = "http.target"; //deprecated
    private const string UrlFull = "url.full";
    private const string HttpUrl = "http.url"; //deprecated
    private const string RpcService = "rpc.service";
    private const string RpcMethod = "rpc.method";
    private const string RpcSystem = "rpc.system";
    private const string DbSystem = "db.system";
    private const string DbName = "db.name";

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
                "Microsoft.AspNetCore" => GetHttpServerNodeId(serviceName, span.Attributes),
                "System.Net.Http" => GetHttpClientNodeId(serviceName, span.Attributes),
                "OpenTelemetry.Instrumentation.GrpcNetClient" => GetRpcNodeId(serviceName, span.Attributes),
                "Npgsql" => GetDatabaseNodeId(serviceName, span.Attributes),
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
    private string? GetHttpServerNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? httpRequestMethod = null;
        string? httpMethod = null;
        string? urlPath = null;
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
                case HttpTarget:
                    httpTarget = attribute.Value.StringValue;
                    break;
            }
        }

        var method = !string.IsNullOrEmpty(httpRequestMethod) ? httpRequestMethod : httpMethod;
        var target = !string.IsNullOrEmpty(urlPath) ? urlPath : httpTarget;

        if (string.IsNullOrEmpty(method) || string.IsNullOrEmpty(target))
        {
            logger.LogWarning("Unable to create HTTP server span id: {otelSpanMethod}, {otelSpanTarget}",
                method, target);
            return null;
        }

        var id = $"{service}-{method}-{target}";
        logger.LogTrace("Span HTTP server id {otelSpanId}", id);

        return id;
    }

    //https://opentelemetry.io/docs/specs/semconv/http/http-spans/
    //https://opentelemetry.io/docs/specs/semconv/attributes-registry/http/
    private string? GetHttpClientNodeId(string service, RepeatedField<KeyValue> attributes)
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
            logger.LogWarning("Unable to create HTTP client span id: {otelSpanMethod}, {otelSpanUrl}",
                method, url);
            return null;
        }

        var id = $"{service}-{method}-{url}";
        logger.LogTrace("Span HTTP client id {otelSpanId}", id);

        return id;
    }

    //https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/
    //https://opentelemetry.io/docs/specs/semconv/attributes-registry/rpc/
    private string? GetRpcNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? rpcService = null;
        string? rpcMethod = null;
        string? rpcSystem = null;

        foreach (var attribute in attributes)
        {
            switch (attribute.Key)
            {
                case RpcService:
                    rpcService = attribute.Value.StringValue;
                    break;
                case RpcMethod:
                    rpcMethod = attribute.Value.StringValue;
                    break;
                case RpcSystem:
                    rpcSystem = attribute.Value.StringValue;
                    break;
            }
        }

        if (string.IsNullOrEmpty(rpcSystem))
        {
            logger.LogWarning("Unable to create RPC span id: {otelSpanRpcSystem}", rpcSystem);
            return null;
        }

        string id;

        if (!string.IsNullOrEmpty(rpcService) || !string.IsNullOrEmpty(rpcMethod))
        {
            id = $"{service}-{rpcSystem}-{rpcService}-{rpcMethod}";
            logger.LogTrace("Span RPC id {otelSpanId}", id);

            return id;
        }

        id = $"{service}-{rpcSystem}";
        logger.LogTrace("Span RPC id {otelSpanId}", id);

        return id;
    }

    private string? GetDatabaseNodeId(string service, RepeatedField<KeyValue> attributes)
    {
        string? dbSystem = null;
        string? dbName = null;

        foreach (var attribute in attributes)
        {
            switch (attribute.Key)
            {
                case DbSystem:
                    dbSystem = attribute.Value.StringValue;
                    break;
                case DbName:
                    dbName = attribute.Value.StringValue;
                    break;
            }
        }

        if (string.IsNullOrEmpty(dbSystem))
        {
            logger.LogWarning("Unable to create Database span id: {otelSpanDatabaseSystem}", dbSystem);
            return null;
        }

        string id;

        if (!string.IsNullOrEmpty(dbName))
        {
            id = $"{service}-{dbSystem}-{dbName}";
            logger.LogTrace("Span Database id {otelSpanId}", id);

            return id;
        }

        id = $"{service}-{dbSystem}";
        logger.LogTrace("Span Database id {otelSpanId}", id);

        return id;
    }
}