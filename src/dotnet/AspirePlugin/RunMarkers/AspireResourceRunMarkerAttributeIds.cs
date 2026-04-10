using JetBrains.TextControl.DocumentMarkup;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[RegisterHighlighter(
    AspireResourceMarkerId,
    Layer = HighlighterLayer.SYNTAX + 1,
    EffectType = EffectType.GUTTER_MARK,
    GutterMarkType = typeof(AspireResourceRunMarkerGutterMark)
)]
public static class AspireResourceRunMarkerAttributeIds
{
    public const string AspireResourceMarkerId = "Aspire Resource Gutter Mark";
}
