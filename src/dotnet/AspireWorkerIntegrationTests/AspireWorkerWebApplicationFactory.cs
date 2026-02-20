using JetBrains.Rider.Aspire.Worker.Configuration;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class AspireWorkerWebApplicationFactory<TProgram> : WebApplicationFactory<TProgram> where TProgram : class
{
    public const string TestToken = "test-token";
    private readonly string[] _supportedSessionTypes = ["project"];

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment(TestingHostEnvironmentExtensions.Testing);

        builder.ConfigureServices(services =>
        {
            var connectionWrapper = services.SingleOrDefault(d => d.ServiceType == typeof(IRdConnectionWrapper));
            if (connectionWrapper != null) services.Remove(connectionWrapper);
            var inMemoryConnectionWrapper = new InMemoryConnectionWrapper();
            services.AddSingleton<IRdConnectionWrapper>(inMemoryConnectionWrapper);
            services.AddSingleton(inMemoryConnectionWrapper);

            services.Configure<DcpSessionOptions>(opts =>
            {
                opts.Token = TestToken;
                opts.SupportedSessionTypes = _supportedSessionTypes;
            });
        });
    }
}