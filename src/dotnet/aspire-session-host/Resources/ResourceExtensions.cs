using Aspire.V1;
using AspireSessionHost.Generated;
using ResourceProperty = AspireSessionHost.Generated.ResourceProperty;
using ResourceType = AspireSessionHost.Generated.ResourceType;

namespace AspireSessionHost.Resources;

internal static class ResourceExtensions
{
    internal static ResourceModel ToModel(this Resource resource) => new(
        resource.Name,
        Map(resource.ResourceType),
        resource.DisplayName,
        resource.Uid,
        resource.HasState ? resource.State : null,
        resource.CreatedAt.ToDateTime(),
        resource.HasExpectedEndpointsCount ? resource.ExpectedEndpointsCount : null,
        resource.Properties.Select(it => it.ToModel()).ToArray(),
        resource.Environment.Select(it => it.ToModel()).ToArray(),
        resource.Endpoints.Select(it => it.ToModel()).ToArray(),
        resource.Services.Select(it => it.ToModel()).ToArray()
    );

    private static ResourceType Map(string type) => type switch
    {
        "Project" => ResourceType.Project,
        "Container" => ResourceType.Container,
        "Executable" => ResourceType.Executable,
        _ => ResourceType.Unknown
    };

    private static ResourceProperty ToModel(this Aspire.V1.ResourceProperty property) => new(
        property.Name,
        property.HasDisplayName ? property.DisplayName : null,
        property.Value.ToString()
    );

    private static ResourceEnvironmentVariable ToModel(this Aspire.V1.EnvironmentVariable variable) => new(
        variable.Name,
        variable.HasValue ? variable.Value : null
    );

    private static ResourceEndpoint ToModel(this Aspire.V1.Endpoint endpoint) => new(
        endpoint.EndpointUrl,
        endpoint.ProxyUrl
    );

    // ReSharper disable once RedundantNameQualifier
    private static ResourceService ToModel(this Aspire.V1.Service service) => new(
        service.Name,
        service.HasAllocatedAddress ? service.AllocatedAddress : null,
        service.HasAllocatedPort ? service.AllocatedPort : null
    );
}