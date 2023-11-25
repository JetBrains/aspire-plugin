using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Features.Running;

namespace AspirePlugin;

[ZoneMarker]
public class ZoneMarker : IRequire<RunnableProjectsZone>;