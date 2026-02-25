var builder = DistributedApplication.CreateBuilder(args);

builder.AddPythonApp("script", "../script", "main.py");

builder.Build().Run();