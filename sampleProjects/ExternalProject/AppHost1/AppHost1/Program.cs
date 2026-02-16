var builder = DistributedApplication.CreateBuilder(args);

builder.AddProject("WebApp", "../../WebApplication1/WebApplication1/WebApplication1.csproj");

builder.Build().Run();