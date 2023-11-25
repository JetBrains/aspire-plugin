using System.Net.WebSockets;
using Microsoft.AspNetCore.Http.HttpResults;

namespace AspireSessionHost;

internal static class SessionEndpoints
{
    internal static void MapSessionEndpoints(this IEndpointRouteBuilder routes)
    {
        var group = routes.MapGroup("/run_session");

        group.MapPut("/", async (Session session, SessionService service) =>
        {
            var id = await service.Create(session);
            return TypedResults.Created($"/run_session/{id}", session);
        });

        group.MapDelete(
            "/{sessionId:guid}",
            async Task<Results<Ok, NoContent>> (Guid sessionId, SessionService service) =>
            {
                var isSuccessful = await service.Delete(sessionId);
                return isSuccessful ? TypedResults.Ok() : TypedResults.NoContent();
            });

        group.MapGet("/notify", async context =>
        {
            if (context.WebSockets.IsWebSocketRequest)
            {
                using var ws = await context.WebSockets.AcceptWebSocketAsync();
                await Receive(ws);
            }
            else
            {
                context.Response.StatusCode = StatusCodes.Status400BadRequest;
            }
        });
    }

    private static async Task Receive(WebSocket webSocket)
    {
        var buffer = new byte[1024 * 4];
        var receiveResult = await webSocket.ReceiveAsync(
            new ArraySegment<byte>(buffer), CancellationToken.None);

        while (!receiveResult.CloseStatus.HasValue)
        {
            receiveResult = await webSocket.ReceiveAsync(
                new ArraySegment<byte>(buffer), CancellationToken.None);
        }

        await webSocket.CloseAsync(
            receiveResult.CloseStatus.Value,
            receiveResult.CloseStatusDescription,
            CancellationToken.None);
    }
}