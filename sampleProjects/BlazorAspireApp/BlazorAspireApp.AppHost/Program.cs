var builder = DistributedApplication.CreateBuilder(args);

builder.AddDotnetProject("blazor", "../BlazorApp/BlazorApp/BlazorApp.csproj");

builder.Build().Run();