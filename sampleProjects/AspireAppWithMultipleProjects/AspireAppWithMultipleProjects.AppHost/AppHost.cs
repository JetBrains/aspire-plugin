using System.Diagnostics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

var builder = DistributedApplication.CreateBuilder(args);

builder.AddDotnetProject("WebApplication1", "../WebApplication1/WebApplication1.csproj");
builder.AddDotnetProject("WebApplication2", "../WebApplication2/WebApplication2.csproj");
builder.AddDotnetProject("WebApplication3", "../WebApplication3/WebApplication3.csproj");
builder.AddDotnetProject("WebApplication4", "../WebApplication4/WebApplication4.csproj");
builder.AddDotnetProject("WebApplication5", "../WebApplication5/WebApplication5.csproj");
builder.AddDotnetProject("WebApplication6", "../WebApplication6/WebApplication6.csproj");
builder.AddDotnetProject("WebApplication7", "../WebApplication7/WebApplication7.csproj");
builder.AddDotnetProject("WebApplication8", "../WebApplication8/WebApplication8.csproj");
builder.AddDotnetProject("WebApplication9", "../WebApplication9/WebApplication9.csproj");
builder.AddDotnetProject("WebApplication10", "../WebApplication10/WebApplication10.csproj");
builder.AddDotnetProject("WebApplication11", "../WebApplication11/WebApplication11.csproj");
builder.AddDotnetProject("WebApplication12", "../WebApplication12/WebApplication12.csproj");
builder.AddDotnetProject("WebApplication13", "../WebApplication13/WebApplication13.csproj");
builder.AddDotnetProject("WebApplication14", "../WebApplication14/WebApplication14.csproj");
builder.AddDotnetProject("WebApplication15", "../WebApplication15/WebApplication15.csproj");

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