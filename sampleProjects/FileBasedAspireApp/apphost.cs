#:sdk Aspire.AppHost.Sdk@13.5.3
#:project .\FileBasedAspireApp.ApiService\FileBasedAspireApp.ApiService.csproj
#:project .\FileBasedAspireApp.Web\FileBasedAspireApp.Web.csproj
#:property UserSecretsId=0169f63d-5f25-4676-8ffe-ebee3ecb224b

var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddProject<Projects.FileBasedAspireApp_ApiService>("apiservice")
    .WithHttpHealthCheck("/health");

builder.AddProject<Projects.FileBasedAspireApp_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();