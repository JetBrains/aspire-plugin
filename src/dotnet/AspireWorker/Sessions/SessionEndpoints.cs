using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading.Channels;
using JetBrains.Rider.Aspire.Worker.AspireHost;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

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

        var group = routes.MapGroup("/run_session").RequireAuthorization();

        group.MapPut("/", CreateSession);

        group.MapDelete("/{sessionId}", DeleteSession);

        group.MapGet("/notify", Notify);
    }

    private static async Task<Results<Created<Session>, BadRequest<ErrorResponse>>> CreateSession(
        Session session,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService)
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            return TypedResults.BadRequest(ProtocolVersionIsNotSupported);
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);

        var createdSession = await hostService.CreateSession(aspireHostId, session);
        if (createdSession is null)
        {
            return TypedResults.BadRequest(AspireHostIsNotFound);
        }

        var (sessionId, error) = createdSession.Value;
        if (sessionId is not null)
        {
            return TypedResults.Created($"/run_session/{sessionId}", session);
        }

        if (error is not null)
        {
            return TypedResults.BadRequest(error);
        }

        var unexpectedError = new ErrorResponse(new ErrorDetail("UnexpectedError", "Unable to create a session"));
        return TypedResults.BadRequest(unexpectedError);
    }

    private static async Task<Results<Ok, NoContent, BadRequest<ErrorResponse>>> DeleteSession(
        string sessionId,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService)
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            return TypedResults.BadRequest(ProtocolVersionIsNotSupported);
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);

        var deletedSession = await hostService.DeleteSession(aspireHostId, sessionId);
        if (deletedSession is null)
        {
            return TypedResults.BadRequest(AspireHostIsNotFound);
        }

        var (deletedSessionId, error) = deletedSession.Value;
        if (deletedSessionId is not null)
        {
            return TypedResults.Ok();
        }

        if (error is not null)
        {
            return TypedResults.BadRequest(error);
        }

        return TypedResults.NoContent();
    }

    private static async Task Notify(
        HttpContext context,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService)
    {
        if (!IsProtocolVersionSupported(apiVersion))
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        var aspireHostId = GetAspireHostId(dcpInstanceId);

        var sessionEventReader = hostService.GetSessionEventReader(aspireHostId);
        if (sessionEventReader is null)
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        if (context.WebSockets.IsWebSocketRequest)
        {
            using var ws = await context.WebSockets.AcceptWebSocketAsync();
            await Receive(ws, sessionEventReader);
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

    private static bool IsProtocolVersionSupported(string apiVersion)
    {
        return string.Equals(apiVersion, CurrentProtocolVersion, StringComparison.InvariantCultureIgnoreCase);
    }

    private static string GetAspireHostId(string dcpInstanceId) => dcpInstanceId[..5];
}