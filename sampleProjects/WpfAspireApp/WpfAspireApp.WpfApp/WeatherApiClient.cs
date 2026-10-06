using System.Net.Http;
using System.Net.Http.Json;

namespace WpfAspireApp.WpfApp;

internal sealed class WeatherApiClient(HttpClient httpClient)
{
    public async Task<WeatherForecast[]> GetForecastAsync(CancellationToken cancellationToken) =>
        await httpClient.GetFromJsonAsync<WeatherForecast[]>("/weatherforecast", cancellationToken) ?? [];
}

internal sealed record WeatherForecast(DateOnly Date, int TemperatureC, string Summary);
