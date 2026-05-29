using JetBrains.Application.UI.Controls.BulbMenu.Anchors;
using JetBrains.Application.UI.Controls.BulbMenu.Items;
using JetBrains.ProjectModel;
using JetBrains.Rider.Aspire.Plugin.AspireResources;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Rider.Backend.Features.RunMarkers;
using JetBrains.TextControl.DocumentMarkup;
using JetBrains.UI.Icons;
using JetBrains.UI.RichText;
using JetBrains.UI.ThemedIcons;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

public class AspireResourceRunMarkerGutterMark()
    : RunMarkerGutterMarkBase<AspireResourceRunMarkerHighlighting>(RunMarkersThemedIcons.RunActions.Id)
{
    protected override IEnumerable<BulbMenuItem> GetBulbMenuItems(
        ISolution solution,
        AspireResourceRunMarkerHighlighting runMarker,
        IHighlighter highlighter)
    {
        var resourceProtocolHost = solution.GetComponent<AspireResourceProtocolHost>();
        var resource = resourceProtocolHost.FindResource(runMarker.ResourceName);
        if (resource == null) yield break;

        foreach (var command in resource.Commands)
        {
            if (command.State != AspireRdResourceCommandState.Enabled) continue;

            var isRestart = command.Name is "restart" or "resource-restart";
            if (isRestart && resource.Type == AspireRdResourceType.Project)
            {
                yield return new BulbMenuItem(
                    new ExecutableItem(() => resourceProtocolHost.ExecuteResourceCommand(
                        resource.Name,
                        command.Name,
                        AspireRdSessionLaunchMode.Run)),
                    new RichText("Restart without Debugger"),
                    AspireIconIds.RestartResourceWithoutDebuggerIconId,
                    BulbMenuAnchors.PermanentItem);

                yield return new BulbMenuItem(
                    new ExecutableItem(() => resourceProtocolHost.ExecuteResourceCommand(
                        resource.Name,
                        command.Name,
                        AspireRdSessionLaunchMode.Debug)),
                    new RichText("Restart with Debugger"),
                    AspireIconIds.RestartResourceWithDebuggerIconId,
                    BulbMenuAnchors.PermanentItem);

                continue;
            }

            yield return new BulbMenuItem(
                new ExecutableItem(() => resourceProtocolHost.ExecuteResourceCommand(resource.Name, command.Name)),
                new RichText(command.DisplayName),
                GetIconForCommand(command),
                BulbMenuAnchors.PermanentItem);
        }
    }

    private static IconId? GetIconForCommand(AspireRdResourceCommand command) => command.Name switch
    {
        "start" or "resource-start" => AspireIconIds.StartResourceIconId,
        "stop" or "resource-stop" => AspireIconIds.StopResourceIconId,
        "restart" or "resource-restart" => AspireIconIds.RestartResourceIconId,
        "rebuild" or "resource-rebuild" => AspireIconIds.RebuildResourceIconId,
        _ => null
    };
}