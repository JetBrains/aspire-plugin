var builder = DistributedApplication.CreateBuilder(args);

var apiService = builder.AddProject<Projects.DefaultAspireSolution_ApiService>("apiservice");

builder.AddProject<Projects.DefaultAspireSolution_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();
