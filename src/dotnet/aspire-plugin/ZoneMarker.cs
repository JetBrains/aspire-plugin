using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Features.Running;

namespace JetBrains.Rider.Aspire;

[ZoneMarker]
public class ZoneMarker : IRequire<RunnableProjectsZone>;