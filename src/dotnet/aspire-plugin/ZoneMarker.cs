using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ProjectModel;

namespace AspirePlugin;

[ZoneMarker]
public class ZoneMarker : IRequire<IProjectModelZone>;