#:sdk Aspire.AppHost.Sdk@13.0.2
#:project .\DefaultAspireSolution.ApiService\DefaultAspireSolution.ApiService.csproj
#:project .\DefaultAspireSolution.Web\DefaultAspireSolution.Web.csproj
#:property UserSecretsId=0169f63d-5f25-4676-8ffe-ebee3ecb224b

var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddProject<Projects.DefaultAspireSolution_ApiService>("apiservice")
    .WithHttpHealthCheck("/health");

builder.AddProject<Projects.DefaultAspireSolution_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithHttpHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();