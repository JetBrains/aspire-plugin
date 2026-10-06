using Projects;

var builder = DistributedApplication.CreateBuilder(args);

var api = builder.AddProject<WinFormsAspireApp_ApiService>("api")
    .WithHttpHealthCheck("/health");

if (OperatingSystem.IsWindows())
{
    builder.AddProject<WinFormsAspireApp_WinFormsApp>("desktop")
        .WithReference(api)
        .WaitFor(api);
}

builder.Build().Run();
