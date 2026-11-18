var builder = DistributedApplication.CreateBuilder(args);

var api = builder.AddDotnetProject("api", "../WinFormsAspireApp.ApiService/WinFormsAspireApp.ApiService.csproj")
    .WithHttpHealthCheck("/health");

if (OperatingSystem.IsWindows())
{
    builder.AddDotnetProject("desktop", "../WinFormsAspireApp.WinFormsApp/WinFormsAspireApp.WinFormsApp.csproj")
        .WithReference(api)
        .WaitFor(api);
}

builder.Build().Run();
