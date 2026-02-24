using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Logging;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace FunctionAspireApp.FunctionApp;

public class MyTrigger
{
    private readonly ILogger<MyTrigger> _logger;

    public MyTrigger(ILogger<MyTrigger> logger)
    {
        _logger = logger;
    }

    [Function("MyTrigger")]
    public IActionResult Run([HttpTrigger(AuthorizationLevel.Anonymous, "get", "post")] HttpRequest req)
    {
        _logger.LogInformation("C# HTTP trigger function processed a request.");
        return new OkObjectResult("Welcome to Azure Functions!");
    }
}