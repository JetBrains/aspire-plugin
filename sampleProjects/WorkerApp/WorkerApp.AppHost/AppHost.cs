var builder = DistributedApplication.CreateBuilder(args);

builder.AddDotnetProject("worker", "../WorkerApp.Worker/WorkerApp.Worker.csproj");

builder.Build().Run();