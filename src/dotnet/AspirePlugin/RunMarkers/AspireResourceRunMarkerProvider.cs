using System;
using System.Collections.Generic;
using System.Linq;
using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.Application.Settings;
using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Rider.Backend.Features.RunMarkers;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[Language(typeof(CSharpLanguage))]
[HighlightingSource(HighlightingTypes = [typeof(IRunMarkerHighlighting)])]
[ZoneMarker(typeof(ILanguageCSharpZone))]
public class AspireResourceRunMarkerProvider : IRunMarkerProvider
{
    private static readonly HashSet<string> OurAppHostFileNames = new(StringComparer.Ordinal)
    {
        "AppHost.cs",
        "Program.cs",
        "apphost.cs"
    };

    public void CollectRunMarkers(IFile file, IContextBoundSettingsStore settings, IHighlightingConsumer consumer)
    {
        if (file is not ICSharpFile csharpFile) return;

        var project = file.GetProject();
        if (project == null || !project.IsAspireHostProject()) return;

        var sourceFile = file.GetSourceFile();
        if (sourceFile == null || !OurAppHostFileNames.Contains(sourceFile.Name)) return;

        var resourceProtocolHost = project.GetSolution().GetComponent<AspireResourceProtocolHost>();
        foreach (var declaration in CollectDeclarations(csharpFile))
        {
            var resource = resourceProtocolHost.FindResource(declaration.ResourceName);
            if (resource == null) continue;
            if (!resource.Commands.Any(command => command.State == AspireRdResourceCommandState.Enabled)) continue;

            var highlighting = new AspireResourceRunMarkerHighlighting(
                project,
                resource.Name,
                declaration.ResourceName,
                GetToolTip(resource),
                AspireResourceRunMarkerAttributeIds.AspireResourceMarkerId,
                declaration.Range,
                file.GetPsiModule().TargetFrameworkId);
            consumer.AddHighlighting(highlighting, declaration.Range);
        }
    }

    public double Priority => RunMarkerProviderPriority.DEFAULT;

    private static IEnumerable<AspireResourceDeclarationInfo> CollectDeclarations(ICSharpFile csharpFile)
    {
        foreach (var declarationStatement in csharpFile.Descendants<IDeclarationStatement>().ToEnumerable())
        {
            if (TryCreateDeclarationInfo(declarationStatement, out var declarationInfo) && declarationInfo is not null)
            {
                yield return declarationInfo;
            }
        }

        foreach (var expressionStatement in csharpFile.Descendants<IExpressionStatement>().ToEnumerable())
        {
            if (TryCreateDeclarationInfo(expressionStatement, out var declarationInfo) && declarationInfo is not null)
            {
                yield return declarationInfo;
            }
        }
    }

    private static bool TryCreateDeclarationInfo(
        IDeclarationStatement declarationStatement,
         out AspireResourceDeclarationInfo? declarationInfo)
    {
        var variableDeclaration = declarationStatement.VariableDeclarations.FirstOrDefault();
        var expression = (variableDeclaration?.Initial as IExpressionInitializer)?.Value;
        return TryCreateDeclarationInfo(declarationStatement, expression, out declarationInfo);
    }

    private static bool TryCreateDeclarationInfo(
        IExpressionStatement expressionStatement,
        out AspireResourceDeclarationInfo? declarationInfo)
    {
        return TryCreateDeclarationInfo(expressionStatement, expressionStatement.Expression, out declarationInfo);
    }

    private static bool TryCreateDeclarationInfo(
        IStatement statement,
        ICSharpExpression? expression,
        out AspireResourceDeclarationInfo? declarationInfo)
    {
        declarationInfo = null;

        if (expression is not IInvocationExpression invocationExpression) return false;

        var rootInvocation = GetRootInvocation(invocationExpression);
        if (!IsAspireAddInvocation(rootInvocation)) return false;

        var resourceName = TryGetResourceName(rootInvocation);
        if (resourceName == null) return false;

        var range = statement.GetDocumentRange().StartOffsetRange();
        if (!range.IsValid()) return false;

        declarationInfo = new AspireResourceDeclarationInfo(resourceName, range);
        return true;
    }

    private static IInvocationExpression GetRootInvocation(IInvocationExpression invocationExpression)
    {
        var current = invocationExpression;
        while (current.InvokedExpression is IReferenceExpression { QualifierExpression: IInvocationExpression qualifierInvocation })
        {
            current = qualifierInvocation;
        }

        return current;
    }

    private static bool IsAspireAddInvocation(IInvocationExpression invocationExpression)
    {
        if (invocationExpression.InvokedExpression is not IReferenceExpression
            {
                QualifierExpression: IReferenceExpression qualifierExpression,
                NameIdentifier: { } nameIdentifier
            })
        {
            return false;
        }

        if (!nameIdentifier.Name.StartsWith("Add", StringComparison.Ordinal)) return false;

        return string.Equals(qualifierExpression.NameIdentifier?.Name, "builder", StringComparison.Ordinal);
    }

    private static string? TryGetResourceName(IInvocationExpression invocationExpression)
    {
        foreach (var argument in invocationExpression.ArgumentList.Arguments)
        {
            if (argument.Value is not ICSharpLiteralExpression literalExpression) continue;
            if (literalExpression.ConstantValue.StringValue is { } resourceName) return resourceName;
        }

        return null;
    }

    private static string GetToolTip(AspireRdResource resource)
    {
        var resourceName = string.IsNullOrEmpty(resource.DisplayName) ? resource.Name : resource.DisplayName;
        return $"Aspire resource '{resourceName}'";
    }

    private sealed record AspireResourceDeclarationInfo(string ResourceName, DocumentRange Range);
}
