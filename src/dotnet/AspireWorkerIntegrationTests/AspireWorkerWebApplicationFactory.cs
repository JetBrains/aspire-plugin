using JetBrains.Rider.Aspire.Worker.AspireHost;
using JetBrains.Rider.Aspire.Worker.Configuration;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class AspireWorkerWebApplicationFactory<TProgram> : WebApplicationFactory<TProgram> where TProgram : class
{
    public const string TestToken = "test-token";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureServices(services =>
        {
            var aspireHostService = services.SingleOrDefault(d => d.ServiceType == typeof(InMemoryAspireHostService));
            if (aspireHostService != null) services.Remove(aspireHostService);
            var inMemoryAspireHostService = new InMemoryAspireHostService();
            services.AddSingleton<IAspireHostService>(inMemoryAspireHostService);
            services.AddSingleton(inMemoryAspireHostService);

            services.Configure<DcpSessionOptions>(opts => opts.Token = TestToken);
        });
    }
}