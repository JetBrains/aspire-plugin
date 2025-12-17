using Microsoft.Extensions.Logging;

namespace AspireSolutionWithXUnit.Tests;

public class WebTests
{
    private static readonly TimeSpan DefaultTimeout = TimeSpan.FromSeconds(300);

    [Fact]
    public async Task GetWebResourceRootReturnsOkStatusCode()
    {
        // Arrange
        var cancellationToken = new CancellationTokenSource(DefaultTimeout).Token;

        var appHost =
            await DistributedApplicationTestingBuilder.CreateAsync<Projects.AspireSolutionWithXUnit_AppHost>(
                cancellationToken);
        appHost.Services.AddLogging(logging =>
        {
            logging.SetMinimumLevel(LogLevel.Debug);
            // Override the logging filters from the app's configuration
            logging.AddFilter(appHost.Environment.ApplicationName, LogLevel.Debug);
            logging.AddFilter("Aspire.", LogLevel.Debug);
            // To output logs to the xUnit.net ITestOutputHelper, consider adding a package from https://www.nuget.org/packages?q=xunit+logging
        });
        appHost.Services.ConfigureHttpClientDefaults(clientBuilder =>
        {
            clientBuilder.AddStandardResilienceHandler();
        });

        await using var app = await appHost.BuildAsync(cancellationToken).WaitAsync(DefaultTimeout, cancellationToken);
        await app.StartAsync(cancellationToken).WaitAsync(DefaultTimeout, cancellationToken);

        // Act
        var httpClient = app.CreateHttpClient("webfrontend");
        await app.ResourceNotifications.WaitForResourceHealthyAsync("webfrontend", cancellationToken)
            .WaitAsync(DefaultTimeout, cancellationToken);
        var response = await httpClient.GetAsync("/", cancellationToken);

        // Assert
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }
}