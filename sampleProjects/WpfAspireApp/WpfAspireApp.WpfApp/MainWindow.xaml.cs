using System.Windows;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace WpfAspireApp.WpfApp;

internal partial class MainWindow : Window
{
    private readonly WeatherApiClient _apiClient;
    private readonly ILogger<MainWindow> _logger;
    private readonly CancellationTokenSource _lifetimeCancellation = new();

    public MainWindow()
    {
        var services = ((App)Application.Current).Services;
        _apiClient = services.GetRequiredService<WeatherApiClient>();
        _logger = services.GetRequiredService<ILogger<MainWindow>>();

        InitializeComponent();
    }

    private async void RefreshForecast(object sender, RoutedEventArgs e)
    {
        RefreshButton.IsEnabled = false;
        Status.Text = "Loading forecast…";

        try
        {
            var forecasts = await _apiClient.GetForecastAsync(_lifetimeCancellation.Token);

            Forecasts.ItemsSource = forecasts;
            Status.Text = $"Loaded {forecasts.Length} forecasts at {DateTime.Now:T}.";
            _logger.LogInformation("Loaded {ForecastCount} weather forecasts", forecasts.Length);
        }
        catch (OperationCanceledException) when (_lifetimeCancellation.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            Status.Text = "Unable to load the forecast. Try refreshing.";
            _logger.LogError(exception, "Failed to load weather forecasts");
        }
        finally
        {
            RefreshButton.IsEnabled = true;
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        _lifetimeCancellation.Cancel();
        _lifetimeCancellation.Dispose();

        base.OnClosed(e);
    }
}
