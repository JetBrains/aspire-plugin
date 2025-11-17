using System.Security.Claims;
using System.Text.Encodings.Web;
using JetBrains.Rider.Aspire.Worker.Configuration;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace JetBrains.Rider.Aspire.Worker.Authentication;

/// <summary>
/// Authentication handler for handling token-based authentication based on the DCP token.
/// </summary>
/// <remarks>
/// See <see href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution"/> for more information on how to configure the DCP token.
/// </remarks>
internal sealed class DcpTokenAuthenticationHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder,
    IOptions<DcpSessionOptions> dcpOptions)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    private const string BearerPrefix = "Bearer ";
    private const string PrincipalName = "dcp";
    private const string UnsupportedScheme = "Unsupported authorization scheme.";
    private const string MissingToken = "Missing token.";
    private const string InvalidToken = "Invalid token.";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var authorization = Request.Headers.Authorization.ToString();
        if (string.IsNullOrEmpty(authorization))
        {
            Logger.LogInformation("Authorization header contained no usable value");
            return Task.FromResult(AuthenticateResult.NoResult());
        }

        if (!authorization.StartsWith(BearerPrefix, StringComparison.OrdinalIgnoreCase))
        {
            Logger.LogInformation("Unsupported authorization scheme");
            return Task.FromResult(AuthenticateResult.Fail(UnsupportedScheme));
        }

        var token = authorization[BearerPrefix.Length..].Trim();
        if (token.Length == 0)
        {
            Logger.LogWarning("Bearer token validation failed.");
            return Task.FromResult(AuthenticateResult.Fail(MissingToken));
        }

        if (!string.Equals(token, dcpOptions.Value.Token, StringComparison.Ordinal))
        {
            return Task.FromResult(AuthenticateResult.Fail(InvalidToken));
        }

        var identity = new ClaimsIdentity(Scheme.Name);
        identity.AddClaim(new Claim(ClaimTypes.NameIdentifier, PrincipalName));
        var principal = new ClaimsPrincipal(identity);
        var ticket = new AuthenticationTicket(principal, Scheme.Name);

        return Task.FromResult(AuthenticateResult.Success(ticket));
    }

    protected override Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status401Unauthorized;
        Response.Headers.Append("WWW-Authenticate", "Bearer");
        return Task.CompletedTask;
    }
}
