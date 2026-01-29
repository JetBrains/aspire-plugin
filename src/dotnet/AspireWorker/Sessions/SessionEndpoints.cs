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
    private static readonly string[] SupportedProtocolVersions = ["2025-10-01"];
    private static readonly string[] SupportedSessionTypes = ["project"];

    private static readonly ErrorResponse ProtocolVersionIsNotSupported = new(new ErrorDetail(
        "ProtocolVersionIsNotSupported",
        "The current protocol version is not supported by the plugin. Probably you should update either Aspire or the plugin."
    ));

    private static readonly ErrorResponse AspireHostIsNotFound = new(new ErrorDetail(
        "AspireHostIsNotFound",
        "Unable to find an Aspire host. Please make sure that Aspire is running."
    ));

    // ReSharper disable once ClassNeverInstantiated.Local
    private class SessionEndpointsLogger;

    internal static void MapSessionEndpoints(this IEndpointRouteBuilder routes)
    {
        routes.MapGet("/info", Info);

        var group = routes.MapGroup("/run_session").RequireAuthorization();

        group.MapPut("/", CreateSession);

        group.MapDelete("/{sessionId}", DeleteSession);

        group.MapGet("/notify", Notify);
    }

    /// <summary>
    /// Used by DCP to get information about the capabilities of the IDE run session endpoint.
    /// </summary>
    /// <see href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#ide-endpoint-information-request">IDE session endpoint requests</see>
    private static Info Info()
    {
        return new Info(
            [..SupportedProtocolVersions],
            [..SupportedSessionTypes]
            );
    }

    /// <summary>
    /// Used to create a new run session for a particular Executable.
    /// </summary>
    /// <remarks>
    /// If the execution session is created successfully, the return status code should be 200 OK or 201 Created.
    /// If the session cannot be created, an appropriate 4xx or 5xx status code should be returned.
    /// The response might also return a description of the problem as part of the status line.
    /// </remarks>
    /// <see href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#create-session-request">IDE session endpoint requests</see>
    private static async Task<Results<Created<Session>, BadRequest<ErrorResponse>>> CreateSession(
        Session session,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService,
        ILogger<SessionEndpointsLogger> logger)
    {
        logger.CreateNewSessionRequestReceived();

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

    /// <summary>
    /// Used to stop an in-progress run session.
    /// </summary>
    /// <remarks>
    /// If the session exists and can be stopped, the IDE should reply with 200 OK status code.
    /// If the session does not exist, the IDE should reply with 204 No Content.
    /// If the session cannot be stopped, an appropriate 4xx or 5xx status code should be returned.
    /// The response might also return a description of the problem as part of the status line.
    /// </remarks>
    /// <see href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#stop-session-request">IDE session endpoint requests</see>
    private static async Task<Results<Ok, NoContent, BadRequest<ErrorResponse>>> DeleteSession(
        string sessionId,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService,
        ILogger<SessionEndpointsLogger> logger)
    {
        logger.DeleteSessionRequestReceived(sessionId);

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

    /// <summary>
    /// Used by DCP to subscribe to run session change notification.
    /// </summary>
    /// <remarks>
    /// If successful, the connection should be upgraded to a WebSocket connection,
    /// which will be then used by the IDE to stream run session change notifications to DCP.
    /// </remarks>
    /// <see href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#subscribe-to-session-change-notifications-request">IDE session endpoint requests</see>
    private static async Task Notify(
        HttpContext context,
        [FromQuery(Name = "api-version")] string apiVersion,
        [FromHeader(Name = "Microsoft-Developer-DCP-Instance-ID")]
        string dcpInstanceId,
        IAspireHostService hostService,
        IHostApplicationLifetime applicationLifetime,
        ILogger<SessionEndpointsLogger> logger)
    {
        logger.NotifyRequestReceived();

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
            await Receive(ws, sessionEventReader, applicationLifetime.ApplicationStopping);
        }
        else
        {
            context.Response.StatusCode = StatusCodes.Status400BadRequest;
        }
    }

    private static async Task Receive(WebSocket webSocket, ChannelReader<ISessionEvent> reader,
        CancellationToken cancellationToken)
    {
        var jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
        };

        try
        {
            await foreach (var value in reader.ReadAllAsync(cancellationToken))
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
                    cancellationToken
                );
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            // Application is shutting down, close the WebSocket gracefully
            if (webSocket.State is WebSocketState.Open or WebSocketState.CloseReceived)
            {
                await webSocket.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Server is shutting down",
                    CancellationToken.None
                );
            }
        }
    }

    private static bool IsProtocolVersionSupported(string apiVersion)
    {
        return SupportedProtocolVersions.Contains(apiVersion, StringComparer.OrdinalIgnoreCase);
    }

    private static string GetAspireHostId(string dcpInstanceId) => dcpInstanceId[..5];
}