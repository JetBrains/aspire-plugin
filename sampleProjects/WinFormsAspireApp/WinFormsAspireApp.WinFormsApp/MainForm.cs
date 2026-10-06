using Microsoft.Extensions.Logging;

namespace WinFormsAspireApp.WinFormsApp;

internal partial class MainForm : Form
{
    private readonly WeatherApiClient _apiClient;
    private readonly ILogger<MainForm> _logger;
    private readonly CancellationTokenSource _lifetimeCancellation = new();

    public MainForm(WeatherApiClient apiClient, ILogger<MainForm> logger)
    {
        _apiClient = apiClient;
        _logger = logger;

        InitializeComponent();
    }

    private async void RefreshForecast(object? sender, EventArgs e)
    {
        RefreshButton.Enabled = false;
        Status.Text = "Loading forecast…";

        try
        {
            var forecasts = await _apiClient.GetForecastAsync(_lifetimeCancellation.Token);

            if (IsDisposed)
            {
                return;
            }

            Forecasts.DataSource = forecasts;
            Status.Text = $"Loaded {forecasts.Length} forecasts at {DateTime.Now:T}.";
            _logger.LogInformation("Loaded {ForecastCount} weather forecasts", forecasts.Length);
        }
        catch (OperationCanceledException) when (_lifetimeCancellation.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            if (!IsDisposed)
            {
                Status.Text = "Unable to load the forecast. Try refreshing.";
            }

            _logger.LogError(exception, "Failed to load weather forecasts");
        }
        finally
        {
            if (!IsDisposed)
            {
                RefreshButton.Enabled = true;
            }
        }
    }

    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        _lifetimeCancellation.Cancel();

        base.OnFormClosed(e);
    }
}
