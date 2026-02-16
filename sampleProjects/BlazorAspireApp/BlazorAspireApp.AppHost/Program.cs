var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject<Projects.BlazorApp>("blazor");

builder.Build().Run();