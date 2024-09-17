using System.Text.Json;
using System.Threading.Tasks;
using JetBrains.Rider.Aspire.SessionHost;
using JetBrains.Rider.Aspire.SessionHost.Resources;
using JetBrains.Rider.Aspire.SessionHost.Sessions;
using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace AspireSessionHost;

public class Program
{
  public static async Task Main(string[] args)
  {
    ParentProcessWatchdog.StartNewIfAvailable();

    var builder = WebApplication.CreateBuilder(args);

    builder.Configuration.AddEnvironmentVariables("Rider_");
    builder.Services.ConfigureOptions<ConfigureResourceServiceOptions>();

    builder.Services.AddGrpc();

    var connection = new Connection(builder.Configuration);
    builder.Services.AddSingleton(connection);

    builder.Services.AddSessionServices();
    builder.Services.AddResourceServices(builder.Configuration);

    builder.Services.ConfigureHttpJsonOptions(it =>
    {
      it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
    });

    var app = builder.Build();

    await app.Services.InitializeSessionServices();
    await app.Services.InitializeResourceServices(app.Configuration);

    app.UseWebSockets();

    app.MapSessionEndpoints();

    app.Run();    
  }
}