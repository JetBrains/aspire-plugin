using System.Globalization;
using Aspire.DashboardService.Proto.V1;
using Google.Protobuf.WellKnownTypes;
using JetBrains.Rider.Aspire.Worker.Generated;
using ResourceCommand = JetBrains.Rider.Aspire.Worker.Generated.ResourceCommand;
using ResourceCommandState = JetBrains.Rider.Aspire.Worker.Generated.ResourceCommandState;
using ResourceProperty = JetBrains.Rider.Aspire.Worker.Generated.ResourceProperty;
using ResourceRelationship = JetBrains.Rider.Aspire.Worker.Generated.ResourceRelationship;
using ResourceType = JetBrains.Rider.Aspire.Worker.Generated.ResourceType;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal static class ResourceExtensions
{
    internal static ResourceModel ToModel(this Resource resource) => new(
        resource.Name,
        MapType(resource.ResourceType),
        resource.DisplayName,
        resource.Uid,
        resource.HasState ? MapState(resource.State) : null,
        resource.HasStateStyle ? MapStyle(resource.StateStyle) : null,
        resource.CreatedAt?.ToDateTime(),
        resource.StartedAt?.ToDateTime(),
        resource.StoppedAt?.ToDateTime(),
        resource.Properties.Select(it => it.ToModel()).ToArray(),
        resource.Environment.Select(it => it.ToModel()).ToArray(),
        resource.Urls.Select(it => it.ToModel()).ToArray(),
        resource.Volumes.Select(it => it.ToModel()).ToArray(),
        resource.HealthReports.Select(it => it.ToModel()).ToArray(),
        resource.Commands.Select(it => it.ToModel()).ToArray(),
        resource.Relationships.Select(it => it.ToModel()).ToArray(),
        resource.IsHidden
    );

    private static ResourceType MapType(string type) => type switch
    {
        "Project" => ResourceType.Project,
        "Container" => ResourceType.Container,
        "Executable" => ResourceType.Executable,
        "Parameter" => ResourceType.Parameter,
        "ExternalService" => ResourceType.ExternalService,
        "MongoDBDatabaseResource" => ResourceType.MongoDB,
        "MySqlDatabaseResource" => ResourceType.MySql,
        "PostgresDatabaseResource" => ResourceType.Postgres,
        "SqlServerDatabaseResource" => ResourceType.SqlServer,
        _ => ResourceType.Unknown
    };

    private static ResourceState MapState(string state) => state switch
    {
        "Finished" => ResourceState.Finished,
        "Exited" => ResourceState.Exited,
        "FailedToStart" => ResourceState.FailedToStart,
        "Starting" => ResourceState.Starting,
        "Running" => ResourceState.Running,
        "Hidden" => ResourceState.Hidden,
        _ => ResourceState.Unknown
    };

    private static ResourceStateStyle MapStyle(string style) => style switch
    {
        "success" => ResourceStateStyle.Success,
        "info" => ResourceStateStyle.Info,
        "warning" => ResourceStateStyle.Warning,
        "error" => ResourceStateStyle.Error,
        _ => ResourceStateStyle.Unknown
    };

    private static ResourceHealthStatus MapHealthStatus(HealthStatus status) => status switch
    {
        HealthStatus.Healthy => ResourceHealthStatus.Healthy,
        HealthStatus.Unhealthy => ResourceHealthStatus.Unhealthy,
        HealthStatus.Degraded => ResourceHealthStatus.Degraded,
        _ => throw new ArgumentOutOfRangeException(nameof(status), status, null)
    };

    private static ResourceProperty ToModel(this global::Aspire.DashboardService.Proto.V1.ResourceProperty property) =>
        new(
            property.Name,
            property.HasDisplayName ? property.DisplayName : null,
            GetStringValue(property.Value),
            property.HasIsSensitive ? property.IsSensitive : null
        );

    private static string? GetStringValue(Value value)
    {
        if (value.HasStringValue)
        {
            return value.StringValue;
        }

        if (value.HasBoolValue)
        {
            return value.BoolValue.ToString();
        }

        if (value.HasNumberValue)
        {
            return value.NumberValue.ToString(CultureInfo.InvariantCulture);
        }

        // ReSharper disable once ConvertIfStatementToReturnStatement
        if (value.HasNullValue)
        {
            return null;
        }

        return value.ToString();
    }

    private static ResourceEnvironmentVariable ToModel(this EnvironmentVariable variable) => new(
        variable.Name,
        variable.HasValue ? variable.Value : null
    );

    private static ResourceUrl ToModel(this Url url) => new(
        url.HasEndpointName ? url.EndpointName : null,
        url.FullUrl,
        url.IsInternal,
        url.IsInactive,
        url.DisplayProperties.SortOrder,
        url.DisplayProperties.DisplayName
    );

    private static ResourceVolume ToModel(this Volume volume) => new(
        volume.Source,
        volume.Target,
        volume.MountType,
        volume.IsReadOnly
    );

    private static ResourceHealthReport ToModel(this HealthReport report) => new(
        MapHealthStatus(report.Status),
        report.Key,
        report.Description,
        report.Exception
    );

    private static ResourceCommand ToModel(this global::Aspire.DashboardService.Proto.V1.ResourceCommand command) => new(
        command.Name,
        command.DisplayName,
        command.HasConfirmationMessage ? command.ConfirmationMessage : null,
        command.IsHighlighted,
        command.HasIconName ? command.IconName : null,
        command.HasDisplayDescription ? command.DisplayDescription : null,
        MapCommandState(command.State)
    );

    private static ResourceCommandState MapCommandState(
        global::Aspire.DashboardService.Proto.V1.ResourceCommandState state) => state switch
    {
        global::Aspire.DashboardService.Proto.V1.ResourceCommandState.Enabled => ResourceCommandState.Enabled,
        global::Aspire.DashboardService.Proto.V1.ResourceCommandState.Disabled => ResourceCommandState.Disabled,
        global::Aspire.DashboardService.Proto.V1.ResourceCommandState.Hidden => ResourceCommandState.Hidden,
        _ => throw new ArgumentOutOfRangeException(nameof(state), state, null)
    };

    private static ResourceRelationship ToModel(
        this global::Aspire.DashboardService.Proto.V1.ResourceRelationship relationship) => new(
        relationship.ResourceName,
        relationship.Type
    );
}