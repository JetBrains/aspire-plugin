var builder = DistributedApplication.CreateBuilder(args);

builder.AddDotnetProject("WebApp", "../../WebApplication1/WebApplication1/WebApplication1.csproj");

builder.Build().Run();