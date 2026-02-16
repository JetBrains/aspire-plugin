var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject<Projects.WorkerApp_Worker>("worker");

builder.Build().Run();