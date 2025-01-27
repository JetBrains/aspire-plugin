package model.sessionHost

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator

object AspireSessionHostRoot : Root() {
    init {
        setting(Kotlin11Generator.Namespace, "com.jetbrains.rider.aspire.generated")
        setting(CSharp50Generator.Namespace, "JetBrains.Rider.Aspire.SessionHost.Generated")
    }
}

@Suppress("unused")
object AspireSessionHostModel : Ext(AspireSessionHostRoot) {
    private val ProcessStarted = structdef {
        field("id", string)
        field("pid", long)
    }

    private val ProcessTerminated = structdef {
        field("id", string)
        field("exitCode", int)
    }

    private val LogReceived = structdef {
        field("id", string)
        field("isStdErr", bool)
        field("message", string)
    }

    private val SessionEnvironmentVariable = structdef {
        field("key", string)
        field("value", string)
    }

    private val SessionModel = structdef {
        field("projectPath", string)
        field("debug", bool)
        field("launchProfile", string.nullable)
        field("disableLaunchProfile", bool)
        field("args", array(string).nullable)
        field("envs", array(SessionEnvironmentVariable).nullable)
    }

    private val SessionCreationResult = structdef {
        field("sessionId", string)
    }

    private val ResourceWrapper = classdef {
        property("model", ResourceModel)
        property("isInitialized", bool).async
        sink("logReceived", ResourceLog)
        call("executeCommand", ResourceCommandRequest, ResourceCommandResponse)
    }

    private val ResourceModel = structdef {
        field("name", string)
        field("type", enum("ResourceType") {
            +"Project"
            +"Container"
            +"Executable"
            +"Unknown"
        })
        field("displayName", string)
        field("uid", string)
        field("state", enum("ResourceState") {
            +"Finished"
            +"Exited"
            +"FailedToStart"
            +"Starting"
            +"Running"
            +"Hidden"
            +"Unknown"
        }.nullable)
        field("stateStyle", enum("ResourceStateStyle") {
            +"Success"
            +"Info"
            +"Warning"
            +"Error"
            +"Unknown"
        }.nullable)
        field("createdAt", dateTime.nullable)
        field("startedAt", dateTime.nullable)
        field("stoppedAt", dateTime.nullable)
        field("properties", array(ResourceProperty))
        field("environment", array(ResourceEnvironmentVariable))
        field("urls", array(ResourceUrl))
        field("volumes", array(ResourceVolume))
        field("healthStatus", ResourceHealthStatus.nullable)
        field("healthReports", array(ResourceHealthReport))
        field("commands", array(ResourceCommand))
    }

    private val ResourceProperty = structdef {
        field("name", string)
        field("displayName", string.nullable)
        field("value", string.nullable)
        field("isSensitive", bool.nullable)
    }

    private val ResourceEnvironmentVariable = structdef {
        field("key", string)
        field("value", string.nullable)
    }

    private val ResourceUrl = structdef {
        field("name", string)
        field("fullUrl", string)
        field("isInternal", bool)
    }

    private val ResourceVolume = structdef {
        field("source", string)
        field("target", string)
        field("mountType", string)
        field("isReadOnly", bool)
    }

    private val ResourceHealthStatus = enum("ResourceHealthStatus") {
        +"Healthy"
        +"Unhealthy"
        +"Degraded"
    }

    private val ResourceHealthReport = structdef {
        field("status", ResourceHealthStatus)
        field("key", string)
        field("description", string)
        field("exception", string)
    }

    private val ResourceCommand = structdef {
        field("commandType", string)
        field("displayName", string)
        field("confirmationMessage", string.nullable)
        field("isHighlighted", bool)
        field("iconName", string.nullable)
        field("displayDescription", string.nullable)
        field("state", enum("ResourceCommandState") {
            +"Enabled"
            +"Disabled"
            +"Hidden"
        })
    }

    private val ResourceLog = structdef {
        field("text", string)
        field("isError", bool)
        field("lineNumber", int)
    }

    private val ResourceCommandRequest = structdef {
        field("commandType", string)
        field("resourceName", string)
        field("resourceType", string)
    }

    private val ResourceCommandResponse = structdef {
        field("kind", enum("ResourceCommandResponseKind") {
            +"Undefined"
            +"Succeeded"
            +"Failed"
            +"Canceled"
        })
        field("errorMessage", string.nullable)
    }

    private val AspireHostModelConfig = structdef {
        field("id", string)
            .documentation = "Unique identifier for the Aspire Host, created from the `DEBUG_SESSION_TOKEN` environment variable"
        field("aspireHostProjectPath", string)
            .documentation = "Path of the Aspire Host .csproj file"
        field("resourceServiceEndpointUrl", string.nullable)
            .documentation = "`DOTNET_RESOURCE_SERVICE_ENDPOINT_URL` environment variable"
        field("resourceServiceApiKey", string.nullable)
            .documentation = "`DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable"
    }

    private val AspireHostModel = classdef {
        field("config", AspireHostModelConfig)

        map("resources", string, ResourceWrapper)
    }

    init {
        callback("createSession", SessionModel, SessionCreationResult.nullable)
        callback("deleteSession", string, bool)

        source("processStarted", ProcessStarted)
        source("processTerminated", ProcessTerminated)
        source("logReceived", LogReceived)

        map("aspireHosts", string, AspireHostModel)
    }
}