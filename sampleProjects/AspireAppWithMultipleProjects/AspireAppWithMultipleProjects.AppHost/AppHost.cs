using System.Diagnostics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject<Projects.WebApplication1>("WebApplication1");
builder.AddProject<Projects.WebApplication2>("WebApplication2");
builder.AddProject<Projects.WebApplication3>("WebApplication3");
builder.AddProject<Projects.WebApplication4>("WebApplication4");
builder.AddProject<Projects.WebApplication5>("WebApplication5");
builder.AddProject<Projects.WebApplication6>("WebApplication6");
builder.AddProject<Projects.WebApplication7>("WebApplication7");
builder.AddProject<Projects.WebApplication8>("WebApplication8");
builder.AddProject<Projects.WebApplication9>("WebApplication9");
builder.AddProject<Projects.WebApplication10>("WebApplication10");
builder.AddProject<Projects.WebApplication11>("WebApplication11");
builder.AddProject<Projects.WebApplication12>("WebApplication12");
builder.AddProject<Projects.WebApplication13>("WebApplication13");
builder.AddProject<Projects.WebApplication14>("WebApplication14");
builder.AddProject<Projects.WebApplication15>("WebApplication15");

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