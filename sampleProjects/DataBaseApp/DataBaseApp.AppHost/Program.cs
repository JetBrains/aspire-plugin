var builder = DistributedApplication.CreateBuilder(args);

var postgres = builder.AddPostgres("postgres")
    .WithLifetime(ContainerLifetime.Persistent);
var postgresdb = postgres.AddDatabase("postgresdb");

var mysql = builder.AddMySql("mysql")
    .WithLifetime(ContainerLifetime.Persistent);
var mysqldb = mysql.AddDatabase("mysqldb");

var sqlserver = builder.AddSqlServer("sqlserver")
    .WithLifetime(ContainerLifetime.Persistent);
var sqlserverdb = sqlserver.AddDatabase("sqlserverdb");

var mongo = builder.AddMongoDB("mongo")
    .WithLifetime(ContainerLifetime.Persistent);
var mongodb = mongo.AddDatabase("mongodb");

var cache = builder.AddRedis("cache")
    .WithLifetime(ContainerLifetime.Persistent);

var apiService = builder.AddDotnetProject("apiservice", "../DataBaseApp.ApiService/DataBaseApp.ApiService.csproj")
        .WithReference(postgresdb)
        .WithReference(mysqldb)
        .WithReference(sqlserverdb)
        .WithReference(mongodb)
        .WithReference(cache);

builder.AddDotnetProject("webfrontend", "../DataBaseApp.Web/DataBaseApp.Web.csproj")
    .WithExternalHttpEndpoints()
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();