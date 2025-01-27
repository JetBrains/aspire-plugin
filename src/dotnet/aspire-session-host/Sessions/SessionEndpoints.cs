using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading.Channels;
using JetBrains.Rider.Aspire.SessionHost.AspireHost;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

internal static class SessionEndpoints
{
    private const string CurrentProtocolVersion = "2024-03-03";

    private static readonly ErrorResponse ProtocolVersionIsNotSupported = new(new ErrorDetail(
        "ProtocolVersionIsNotSupported",
        "The current protocol version is not supported by the plugin. Probably you should update either Aspire or the plugin."
    ));

    private static readonly ErrorResponse AspireHostIsNotFound = new(new ErrorDetail(
        "AspireHostIsNotFound",
        "Unable to find an Aspire host. Please make sure that Aspire is running."
    ));

    internal static void MapSessionEndpoints(this IEndpointRouteBuilder routes)
    {
        routes.MapGet("/info", () => new Info([CurrentProtocolVersion]));

        var group = routes.MapGroup("/run_session");

        group.MapPut("/", CreateSession);

        group.MapDelete("/{sessionId}", DeleteSession);

        group.MapGet("/notify", Notify);
    }

    private static bool IsProtocolVersionSupported(string apiVersion)
    {
        return string.Equals(apiVersion, CurrentProtocolVersion, StringComparison.InvariantCultureIgnoreCase);
    }

    private static async Task<Results<Created<Session>, BadRequest<ErrorResponse>>> CreateSession(
        Session session,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")] string dcpInstanceId,
        AspireHostService hostService
    )
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            return TypedResults.BadRequest(ProtocolVersionIsNotSupported);
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);
        var aspireHost = hostService.GetAspireHost(aspireHostId);
        if (aspireHost is null)
        {
            return TypedResults.BadRequest(AspireHostIsNotFound);
        }

        var (sessionId, error) = await aspireHost.Create(session);
        if (sessionId != null)
        {
            return TypedResults.Created($"/run_session/{sessionId}", session);
        }

        if (error != null)
        {
            return TypedResults.BadRequest(error);
        }

        var unexpectedError = new ErrorResponse(new ErrorDetail("UnexpectedError", "Unable to create a session"));
        return TypedResults.BadRequest(unexpectedError);
    }

    private static async Task<Results<Ok, NoContent, BadRequest<ErrorResponse>>> DeleteSession(
        string sessionId,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")] string dcpInstanceId,
        AspireHostService hostService
    )
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            return TypedResults.BadRequest(ProtocolVersionIsNotSupported);
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);
        var aspireHost = hostService.GetAspireHost(aspireHostId);
        if (aspireHost is null)
        {
            return TypedResults.BadRequest(AspireHostIsNotFound);
        }

        var (deletedSessionId, error) = await aspireHost.Delete(sessionId);
        if (deletedSessionId != null)
        {
            return TypedResults.Ok();
        }

        if (error != null)
        {
            return TypedResults.BadRequest(error);
        }

        return TypedResults.NoContent();
    }

    private static string GetAspireHostId(string dcpInstanceId) => dcpInstanceId[..5];

    private static async Task Notify(
        HttpContext context,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")] string dcpInstanceId,
        AspireHostService hostService
    )
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);
        var aspireHost = hostService.GetAspireHost(aspireHostId);
        if (aspireHost is null)
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        if (context.WebSockets.IsWebSocketRequest)
        {
            using var ws = await context.WebSockets.AcceptWebSocketAsync();
            await Receive(ws, aspireHost.SessionEventReader);
        }
        else
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
        }
    }

    private static async Task Receive(WebSocket webSocket, ChannelReader<ISessionEvent> reader)
    {
        var jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
        };

        await foreach (var value in reader.ReadAllAsync())
        {
            if (webSocket.State is WebSocketState.Closed or WebSocketState.Aborted)
            {
                break;
            }

            var jsonString = JsonSerializer.Serialize(value, jsonOptions);
            var bytes = Encoding.UTF8.GetBytes(jsonString);
            var arraySegment = new ArraySegment<byte>(bytes, 0, bytes.Length);

            await webSocket.SendAsync(arraySegment,
                WebSocketMessageType.Text,
                true,
                CancellationToken.None
            );
        }
    }
}