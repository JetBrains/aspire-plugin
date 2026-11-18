var builder = DistributedApplication.CreateBuilder(args);

builder.AddDotnetProject("library", "../ClassLibrary1/ClassLibrary1.csproj", options => options.LaunchProfileName = "Library");

builder.Build().Run();
