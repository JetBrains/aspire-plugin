var builder = DistributedApplication.CreateBuilder(args);

var postgres = builder.AddPostgres("postgres", port: 52221)
    .WithLifetime(ContainerLifetime.Persistent);
var postgresdb = postgres.AddDatabase("postgresdb");

var mysql = builder.AddMySql("mysql", port: 52222)
    .WithLifetime(ContainerLifetime.Persistent);
var mysqldb = mysql.AddDatabase("mysqldb");

var sqlserver = builder.AddSqlServer("sqlserver", port: 52223)
    .WithLifetime(ContainerLifetime.Persistent);
var sqlserverdb = sqlserver.AddDatabase("sqlserverdb");

var mongo = builder.AddMongoDB("mongo", port: 52224)
    .WithLifetime(ContainerLifetime.Persistent);
var mongodb = mongo.AddDatabase("mongodb");

var cache = builder.AddRedis("cache", port: 52225)
    .WithLifetime(ContainerLifetime.Persistent);

var apiService = builder.AddProject<Projects.DataBaseApp_ApiService>("apiservice")
        .WithReference(postgresdb)
        .WithReference(mysqldb)
        .WithReference(sqlserverdb)
        .WithReference(mongodb)
        .WithReference(cache);

builder.AddProject<Projects.DataBaseApp_Web>("webfrontend")
    .WithExternalHttpEndpoints()
    .WithReference(apiService)
    .WaitFor(apiService);

builder.Build().Run();