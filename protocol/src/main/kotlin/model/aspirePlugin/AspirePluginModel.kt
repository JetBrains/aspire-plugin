package model.aspirePlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rider.model.nova.ide.IdeRoot.RdPath
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object AspirePluginModel : Ext(SolutionModel.Solution) {
    private val ReferenceProjectsFromAppHostRequest = structdef {
        field("hostProjectFilePath", RdPath)
        field("projectFilePaths", immutableList(RdPath))
    }

    private val ReferenceProjectsFromAppHostResponse = structdef {
        field("referencedProjectFilePaths", immutableList(RdPath))
    }

    private val ReferenceServiceDefaultsFromProjectsRequest = structdef {
        field("sharedProjectFilePath", RdPath)
        field("projectFilePaths", immutableList(RdPath))
    }

    private val ReferenceServiceDefaultsFromProjectsResponse = structdef {
        field("projectFilePathsWithReference", immutableList(RdPath))
    }

    private val StartAspireHostRequest = structdef {
        field("unitTestRunId", string)
        field("aspireHostProjectPath", RdPath)
        field("underDebugger", bool)
    }

    private val AspireHostEnvironmentVariable = structdef {
        field("key", string)
        field("value", string)
    }

    private val StartAspireHostResponse = structdef {
        field("environmentVariables", array(AspireHostEnvironmentVariable))
    }

    private val StopAspireHostRequest = structdef {
        field("unitTestRunId", string)
    }

    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.aspire.generated")
        setting(CSharp50Generator.Namespace, "JetBrains.Rider.Aspire.Plugin.Generated")

        call("getProjectOutputType", RdPath, string.nullable)
        call(
            "referenceProjectsFromAppHost",
            ReferenceProjectsFromAppHostRequest,
            ReferenceProjectsFromAppHostResponse.nullable
        )
        call(
            "referenceServiceDefaultsFromProjects",
            ReferenceServiceDefaultsFromProjectsRequest,
            ReferenceServiceDefaultsFromProjectsResponse.nullable
        )
        callback("startAspireHost", StartAspireHostRequest, StartAspireHostResponse).async
        callback("stopAspireHost", StopAspireHostRequest, void).async
        sink("unitTestRunCancelled", string).async
    }
}