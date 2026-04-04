using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;

namespace JetBrains.Rider.Aspire.Plugin.AspireResources;

internal static class AspireResourceDeclarationCollector
{
    private static readonly HashSet<string> OurAppHostFileNames = new(StringComparer.Ordinal)
    {
        "AppHost.cs",
        "Program.cs",
        "apphost.cs"
    };

    public static bool IsApplicable(IFile file)
    {
        if (file is not ICSharpFile) return false;

        var project = file.GetProject();
        if (project == null || !project.IsAspireHostProject()) return false;

        var sourceFile = file.GetSourceFile();
        return sourceFile != null && OurAppHostFileNames.Contains(sourceFile.Name);
    }

    public static IEnumerable<AspireResourceDeclarationInfo> Collect(ICSharpFile csharpFile)
    {
        foreach (var declarationStatement in csharpFile.Descendants<IDeclarationStatement>())
        {
            if (TryCreateDeclarationInfo(declarationStatement, out var declarationInfo) && declarationInfo is not null)
            {
                yield return declarationInfo;
            }
        }

        foreach (var expressionStatement in csharpFile.Descendants<IExpressionStatement>())
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
}

internal sealed record AspireResourceDeclarationInfo(string ResourceName, DocumentRange Range);
