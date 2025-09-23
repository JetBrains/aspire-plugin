using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Features.Running;

namespace JetBrains.Rider.Aspire.Plugin;

[ZoneMarker]
public class ZoneMarker : IRequire<RunnableProjectsZone>;