using System.Collections.Generic;
using JetBrains.Application.UI.Controls.BulbMenu.Anchors;
using JetBrains.Application.UI.Controls.BulbMenu.Items;
using JetBrains.ProjectModel;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Rider.Backend.Features.RunMarkers;
using JetBrains.TextControl.DocumentMarkup;
using JetBrains.UI.RichText;
using JetBrains.UI.ThemedIcons;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

public class AspireResourceRunMarkerGutterMark : RunMarkerGutterMarkBase<AspireResourceRunMarkerHighlighting>
{
    public AspireResourceRunMarkerGutterMark()
        : base(RunMarkersThemedIcons.RunActions.Id)
    {
    }

    protected override IEnumerable<BulbMenuItem> GetBulbMenuItems(
        ISolution solution,
        AspireResourceRunMarkerHighlighting runMarker,
        IHighlighter highlighter)
    {
        var resourceProtocolHost = solution.GetComponent<AspireResourceProtocolHost>();
        var resource = resourceProtocolHost.FindResource(runMarker.DeclarationResourceName);
        if (resource == null) yield break;

        foreach (var command in resource.Commands)
        {
            if (command.State != AspireRdResourceCommandState.Enabled) continue;

            yield return new BulbMenuItem(
                new ExecutableItem(() => resourceProtocolHost.ExecuteResourceCommand(resource.Name, command.Name)),
                new RichText(command.DisplayName),
                IconId,
                BulbMenuAnchors.PermanentBackgroundItems);
        }
    }
}
