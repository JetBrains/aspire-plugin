var builder = DistributedApplication.CreateBuilder(args);

var storage = builder.AddAzureStorage("storage")
    .RunAsEmulator();

builder.AddAzureFunctionsProject<Projects.FunctionApp>("functionapp")
    .WithHostStorage(storage)
    .WithExternalHttpEndpoints();

builder.Build().Run();
