using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Features.Running;
using JetBrains.ReSharper.Psi.CSharp;

namespace JetBrains.Rider.Aspire.Plugin;

[ZoneMarker]
public class ZoneMarker : IRequire<RunnableProjectsZone>, IRequire<DaemonZone>, IRequire<ILanguageCSharpZone>;
