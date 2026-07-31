var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject<Projects.WpfAspireApp_WpfApp>("desktop");

builder.Build().Run();
