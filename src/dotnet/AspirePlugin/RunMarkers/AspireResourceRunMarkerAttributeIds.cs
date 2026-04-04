using JetBrains.TextControl.DocumentMarkup;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[RegisterHighlighter(
    AspireResourceRunMarkerAttributeIds.AspireResourceMarkerId,
    Layer = HighlighterLayer.SYNTAX + 1,
    EffectType = EffectType.GUTTER_MARK,
    GutterMarkType = typeof(AspireResourceRunMarkerGutterMark)
)]
public static class AspireResourceRunMarkerAttributeIds
{
    public const string AspireResourceMarkerId = "Rider Aspire Resource Gutter Mark";
}
