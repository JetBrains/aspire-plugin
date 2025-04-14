var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddProject<Projects.DefaultAspireSolution_ApiService>("apiservice")
    .WithHttpsHealthCheck("/health");

builder.AddProject<Projects.DefaultAspireSolution_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithHttpsHealthCheck("/health")
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();
