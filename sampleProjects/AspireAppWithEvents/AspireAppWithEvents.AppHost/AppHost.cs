using System.Diagnostics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddProject<Projects.AspireAppWithEvents_ApiService>("apiservice")
    .WithHttpHealthCheck("/health");

builder.AddProject<Projects.AspireAppWithEvents_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

var sw = new Stopwatch();
builder.Eventing.Subscribe<BeforeStartEvent>(
    (@event, cancellationToken) =>
    {
        var logger = @event.Services.GetRequiredService<ILogger<Program>>();
        logger.LogInformation("BeforeStartEvent. Stopwatch started.");
        sw.Start();
        return Task.CompletedTask;
    });

builder.Eventing.Subscribe<AfterResourcesCreatedEvent>(
    (@event, cancellationToken) =>
    {
        sw.Stop();
        var logger = @event.Services.GetRequiredService<ILogger<Program>>();
        logger.LogInformation("AfterResourcesCreatedEvent. Stopwatch elapsed: {Elapsed}.", sw.Elapsed);
        return Task.CompletedTask;
    });

builder.Build().Run();