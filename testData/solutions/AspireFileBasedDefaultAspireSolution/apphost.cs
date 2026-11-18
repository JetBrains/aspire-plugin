#:sdk Aspire.AppHost.Sdk@17.0.0
#:package Aspire.Hosting.Dotnet@17.0.0
#:property UserSecretsId=0169f63d-5f25-4676-8ffe-ebee3ecb224b

var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddDotnetProject("apiservice", "./DefaultAspireSolution.ApiService/DefaultAspireSolution.ApiService.csproj")
    .WithHttpHealthCheck("/health");

builder.AddDotnetProject("webfrontend", "./DefaultAspireSolution.Web/DefaultAspireSolution.Web.csproj")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();