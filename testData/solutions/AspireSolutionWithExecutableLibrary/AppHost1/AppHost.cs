var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject<Projects.ClassLibrary1>("library", "Library");

builder.Build().Run();