#:package Aspire.Hosting.AppHost@17.0.0
#:package Aspire.Hosting.Dotnet@17.0.0
#:property UserSecretsId=0169f63d-5f25-4676-8ffe-ebee3ecb224b

using System.Runtime.CompilerServices;

var options = new DistributedApplicationOptions
{
    Args = args,
    ProjectDirectory = GetAppHostDirectory()
};
var builder = DistributedApplication.CreateBuilder(options);

var apiService = builder.AddDotnetProject("apiservice", "./AspireFileBasedSolution.ApiService/AspireFileBasedSolution.ApiService.csproj")
    .WithHttpHealthCheck("/health");

builder.AddDotnetProject("webfrontend", "./AspireFileBasedSolution.Web/AspireFileBasedSolution.Web.csproj")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();

// Aspire can take the project directory from Rider's temporary .csproj.
static string GetAppHostDirectory([CallerFilePath] string sourceFile = "") => Path.GetDirectoryName(sourceFile)!;
