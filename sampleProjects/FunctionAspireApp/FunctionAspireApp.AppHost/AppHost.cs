var builder = DistributedApplication.CreateBuilder(args);

var storage = builder.AddAzureStorage("storage")
    .RunAsEmulator();

builder.AddAzureFunctionsProject<Projects.FunctionAspireApp_FunctionApp>("functions")
    .WithHostStorage(storage);

builder.Build().Run();