package model.aspirePlugin

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object AspirePluginModel : Ext(SolutionModel.Solution) {
    private val ReferenceProjectsFromAppHostRequest = structdef {
        field("hostProjectFilePath", string)
        field("projectFilePaths", immutableList(string))
    }

    private val ReferenceServiceDefaultsFromProjectsRequest = structdef {
        field("sharedProjectFilePath", string)
        field("projectFilePaths", immutableList(string))
    }

    private val StartSessionHostRequest = structdef {
        field("unitTestRunId", string)
        field("aspireHostProjectPath", string)
        field("underDebugger", bool)
    }

    private val SessionHostEnvironmentVariable = structdef {
        field("key", string)
        field("value", string)
    }

    private val StartSessionHostResponse = structdef {
        field("environmentVariables", array(SessionHostEnvironmentVariable))
    }

    private val StopSessionHostRequest = structdef {
        field("unitTestRunId", string)
    }

    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.rider.aspire.generated")
        setting(CSharp50Generator.Namespace, "JetBrains.Rider.Aspire.Generated")

        call("getProjectOutputType", string, string.nullable)
        call("referenceProjectsFromAppHost", ReferenceProjectsFromAppHostRequest, void)
        call("referenceServiceDefaultsFromProjects", ReferenceServiceDefaultsFromProjectsRequest, void)
        callback("startSessionHost", StartSessionHostRequest, StartSessionHostResponse).async
        callback("stopSessionHost", StopSessionHostRequest, void).async
        sink("unitTestRunCancelled", string).async
    }
}