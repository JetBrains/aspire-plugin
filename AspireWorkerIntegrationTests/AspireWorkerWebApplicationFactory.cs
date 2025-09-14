using Microsoft.AspNetCore.Mvc.Testing;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class AspireWorkerWebApplicationFactory<TProgram> : WebApplicationFactory<TProgram> where TProgram : class;