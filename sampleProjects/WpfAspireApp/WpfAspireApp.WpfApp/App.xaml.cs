using System.Windows;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace WpfAspireApp.WpfApp;

internal partial class App : Application
{
    private IHost? _host;

    public IServiceProvider Services { get; private set; } = default!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        _host = Host.CreateDefaultBuilder(e.Args)
            .ConfigureServices(services =>
            {
                services.AddServiceDiscovery();
                services.AddHttpClient<WeatherApiClient>(client =>
                {
                    client.BaseAddress = new Uri("https+http://api");
                }).AddServiceDiscovery();
            })
            .Build();

        Services = _host.Services;
        _host.Start();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _host?.Dispose();
        base.OnExit(e);
    }
}
