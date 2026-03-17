package model.aspireWorker

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator

object AspireWorkerRoot : Root() {
    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.aspire.generated")
        setting(CSharp50Generator.Namespace, "JetBrains.Rider.Aspire.Worker.Generated")
    }
}

@Suppress("unused")
object AspireWorkerModel : Ext(AspireWorkerRoot) {
    private val ProcessStarted = structdef {
        field("id", string)
            .documentation = "The ID of the run session that the notification is related to"
        field("pid", long)
            .documentation = "The process ID of the service process associated with the run session"
    }

    private val ProcessTerminated = structdef {
        field("id", string)
            .documentation = "The ID of the run session that the notification is related to"
        field("exitCode", int)
            .documentation = "The exit code of the process associated with the run session"
    }

    private val LogReceived = structdef {
        field("id", string)
            .documentation = "The ID of the run session that the notification is related to"
        field("isStdErr", bool)
            .documentation = "True if the output comes from standard error stream, otherwise false (implying standard output stream)"
        field("message", string)
            .documentation = "The text written by the service program"
    }

    private val MessageReceived = structdef {
        field("id", string)
            .documentation = "The ID of the run session that the notification is related to"
        field("level", enum("MessageLevel") {
            +"Error"
            +"Info"
            +"Debug"
        })
        field("message", string)
            .documentation = "The content of the message"
        field("error", ErrorCode.nullable)
            .documentation = "The error code. Only valid and required for error messages"
    }

    private val SessionEnvironmentVariable = structdef {
        field("key", string)
        field("value", string)
    }

    private val CreateSessionRequest = basestruct {
        field("dcpInstancePrefix", string)
        field("debug", bool)
        field("args", array(string).nullable)
        field("envs", array(SessionEnvironmentVariable).nullable)
    }

    private val CreateProjectSessionRequest = structdef extends CreateSessionRequest {
        field("projectPath", string)
        field("launchProfile", string.nullable)
        field("disableLaunchProfile", bool)
    }

    private val CreatePythonSessionRequest = structdef extends CreateSessionRequest {
        field("programPath", string)
        field("interpreterPath", string.nullable)
        field("module", string.nullable)
    }

    private val CreateSessionResponse = structdef {
        field("sessionId", string.nullable)
        field("error", ErrorCode.nullable)
    }

    private val DeleteSessionRequest = structdef {
        field("dcpInstancePrefix", string)
        field("sessionId", string)
    }

    private val DeleteSessionResponse = structdef {
        field("sessionId", string.nullable)
            .documentation = "The field will be null if the session cannot be found"
        field("error", ErrorCode.nullable)
    }

    private val AspireHostModelConfig = structdef {
        field("id", string)
            .documentation =
            "Unique identifier for the Aspire Host, created from the `DCP_INSTANCE_ID_PREFIX` environment variable"
        field("aspireHostProjectPath", string)
            .documentation = "Path of the Aspire Host .csproj file"
        field("resourceServiceEndpointUrl", string.nullable)
            .documentation = "`ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` environment variable"
        field("resourceServiceApiKey", string.nullable)
            .documentation = "`ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable"
        field("otlpEndpointUrl", string.nullable)
            .documentation = "`ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable"
        field("aspireHostProjectUrl", string.nullable)
            .documentation = "URL of the Aspire Host dashboard"
    }

    private val AspireHostModel = classdef {
        field("config", AspireHostModelConfig)

        source("processStarted", ProcessStarted)
            .documentation = "The notification is emitted when the run is started or the IDE restarts the service."
        source("processTerminated", ProcessTerminated)
            .documentation = "The notification is emitted when the session is terminated (the program ends, or is terminated by the developer)"
        source("logReceived", LogReceived)
            .documentation = "The notification is emitted when the service program writes something to standard output stream (stdout) or standard error (stderr)"
        source("messageReceived", MessageReceived)
            .documentation = "The notification is emitted when the IDE needs to notify the client (and the Aspire developer) about asynchronous events related to a debug session"

    }

    private val ErrorCode = enum {
        +"AspireAppHostNotFound"
        +"UnsupportedLaunchConfigurationType"
        +"AspireSessionNotFound"
        +"DotNetProjectNotFound"
        +"Unexpected"
    }

    init {
        map("aspireHosts", string, AspireHostModel)

        callback("createSession", CreateSessionRequest, CreateSessionResponse)
            .documentation = "Used to create a new run session for a particular Executable"
        callback("deleteSession", DeleteSessionRequest, DeleteSessionResponse)
            .documentation = "Used to stop an in-progress run session"
    }
}
