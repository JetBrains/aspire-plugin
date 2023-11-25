using System.Net.WebSockets;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);
builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

var app = builder.Build();

app.UseWebSockets();

app.MapPut("/run_session", (Session session) =>
{
    app.Logger.LogInformation("Session request {session}", session);
    return TypedResults.Created();
});

app.MapDelete("/run_session/{sessionId:guid}", (Guid sessionId) =>
{
    return TypedResults.Ok();
});

app.MapGet("/run_session/notify", async context =>
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

app.Run();

static async Task Receive(WebSocket webSocket)
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

record Session(
    string ProjectPath,
    bool Debug,
    EnvironmentVariable[] Env,
    string[] Args
);

record EnvironmentVariable(string Name, string Value);