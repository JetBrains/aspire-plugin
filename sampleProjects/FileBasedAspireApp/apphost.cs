#:sdk Aspire.AppHost.Sdk@17.0.0
#:package Aspire.Hosting.Dotnet@17.0.0
#:property UserSecretsId=0169f63d-5f25-4676-8ffe-ebee3ecb224b

var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddDotnetProject("apiservice", "./FileBasedAspireApp.ApiService/FileBasedAspireApp.ApiService.csproj")
    .WithHttpHealthCheck("/health");

builder.AddDotnetProject("webfrontend", "./FileBasedAspireApp.Web/FileBasedAspireApp.Web.csproj")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();