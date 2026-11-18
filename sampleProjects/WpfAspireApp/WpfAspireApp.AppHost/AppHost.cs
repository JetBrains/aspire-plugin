var builder = DistributedApplication.CreateBuilder(args);

var api = builder.AddDotnetProject("api", "../WpfAspireApp.ApiService/WpfAspireApp.ApiService.csproj")
    .WithHttpHealthCheck("/health");

if (OperatingSystem.IsWindows())
{
    builder.AddDotnetProject("desktop", "../WpfAspireApp.WpfApp/WpfAspireApp.WpfApp.csproj")
        .WithReference(api)
        .WaitFor(api);
}

builder.Build().Run();
