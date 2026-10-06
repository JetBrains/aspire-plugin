using Projects;

var builder = DistributedApplication.CreateBuilder(args);

var api = builder.AddProject<WpfAspireApp_ApiService>("api")
    .WithHttpHealthCheck("/health");

if (OperatingSystem.IsWindows())
{
    builder.AddProject<WpfAspireApp_WpfApp>("desktop")
        .WithReference(api)
        .WaitFor(api);
}

builder.Build().Run();
