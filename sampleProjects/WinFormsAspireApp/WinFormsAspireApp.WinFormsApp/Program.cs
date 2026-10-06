using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace WinFormsAspireApp.WinFormsApp;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();

        using var host = Host.CreateDefaultBuilder(args)
            .ConfigureServices(services =>
            {
                services.AddServiceDiscovery();
                services.AddHttpClient<WeatherApiClient>(client =>
                {
                    client.BaseAddress = new Uri("https+http://api");
                }).AddServiceDiscovery();
                services.AddTransient<MainForm>();
            })
            .Build();

        host.Start();

        try
        {
            Application.Run(host.Services.GetRequiredService<MainForm>());
        }
        finally
        {
            host.StopAsync().GetAwaiter().GetResult();
        }
    }
}
