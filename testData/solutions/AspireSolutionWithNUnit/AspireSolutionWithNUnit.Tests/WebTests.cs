using Microsoft.Extensions.Logging;

namespace AspireSolutionWithNUnit.Tests;

public class WebTests
{
    private static readonly TimeSpan DefaultTimeout = TimeSpan.FromSeconds(30);

    [Test]
    public async Task GetWebResourceRootReturnsOkStatusCode()
    {
        // Arrange
        var cancellationToken = TestContext.CurrentContext.CancellationToken;

        var appHost =
            await DistributedApplicationTestingBuilder.CreateAsync<Projects.AspireSolutionWithNUnit_AppHost>(
                cancellationToken);
        appHost.Services.AddLogging(logging =>
        {
            logging.SetMinimumLevel(LogLevel.Debug);
            // Override the logging filters from the app's configuration
            logging.AddFilter(appHost.Environment.ApplicationName, LogLevel.Debug);
            logging.AddFilter("Aspire.", LogLevel.Debug);
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
        Assert.That(response.StatusCode, Is.EqualTo(HttpStatusCode.OK));
    }
}