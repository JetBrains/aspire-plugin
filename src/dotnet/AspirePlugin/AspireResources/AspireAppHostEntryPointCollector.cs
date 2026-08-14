using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;

namespace JetBrains.Rider.Aspire.Plugin.AspireResources;

/// <summary>
/// Locates the <c>DistributedApplication.CreateBuilder(...)</c> call that starts an Aspire AppHost.
/// </summary>
/// <remarks>
/// The Aspire CLI runner debugs the AppHost by making it block inside <c>CreateBuilder</c> until a debugger
/// attaches, so everything at or above that line has already run by then. The frontend uses the line reported
/// here to tell the user that breakpoints there cannot be hit.
/// </remarks>
internal static class AspireAppHostEntryPointCollector
{
    private const string DistributedApplicationTypeName = "DistributedApplication";
    private const string CreateBuilderMethodName = "CreateBuilder";

    /// <summary>
    /// Returns the zero-based document line of the first <c>DistributedApplication.CreateBuilder(...)</c>
    /// invocation in <paramref name="csharpFile"/>, or <c>null</c> when the file contains none.
    /// </summary>
    internal static int? TryFindCreateBuilderLine(ICSharpFile csharpFile)
    {
        foreach (var invocationExpression in csharpFile.Descendants<IInvocationExpression>())
        {
            if (!IsCreateBuilderInvocation(invocationExpression)) continue;

            var startOffset = invocationExpression.GetDocumentRange().StartOffset;
            if (!startOffset.IsValid()) continue;

            // DocumentCoords.Line is zero-based, which is exactly what XLineBreakpoint.getLine() on the
            // frontend reports, so the line needs no adjustment on either side of the protocol.
            return (int)startOffset.ToDocumentCoords().Line;
        }

        return null;
    }

    /// <remarks>
    /// Matched syntactically, without symbol resolution, the same way
    /// <see cref="AspireResourceDeclarationCollector"/> matches `builder.AddXxx(...)`: a `using static` or an
    /// aliased type is missed, which fails closed (no line, so no marker) rather than reporting a wrong line.
    /// </remarks>
    private static bool IsCreateBuilderInvocation(IInvocationExpression invocationExpression)
    {
        if (invocationExpression.InvokedExpression is not IReferenceExpression
            {
                QualifierExpression: IReferenceExpression qualifierExpression,
                NameIdentifier: { } nameIdentifier
            })
        {
            return false;
        }

        if (!string.Equals(nameIdentifier.Name, CreateBuilderMethodName, StringComparison.Ordinal)) return false;

        return string.Equals(
            qualifierExpression.NameIdentifier?.Name,
            DistributedApplicationTypeName,
            StringComparison.Ordinal);
    }
}
