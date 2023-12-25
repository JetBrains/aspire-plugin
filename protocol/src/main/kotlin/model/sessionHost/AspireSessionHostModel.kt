package model.sessionHost

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator

object AspireSessionHostRoot : Root() {
    init {
        setting(Kotlin11Generator.Namespace, "me.rafaelldi.aspire.generated")
        setting(CSharp50Generator.Namespace, "AspireSessionHost.Generated")
    }
}

@Suppress("unused")
object AspireSessionHostModel : Ext(AspireSessionHostRoot) {
    private val EnvironmentVariableModel = structdef {
        field("key", string)
        field("value", string)
    }

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

    private val MetricBase = basestruct {
        field("serviceName", string)
        field("scope", string)
        field("name", string)
        field("description", string.nullable)
        field("unit", string.nullable)
        field("timeStamp", long)
    }

    private val MetricDouble = structdef extends MetricBase {
        field("value", double)
    }

    private val MetricLong = structdef extends MetricBase {
        field("value", long)
    }

    private val SessionModel = classdef {
        field("id", string)
        field("projectPath", string)
        field("debug", bool)
        field("envs", array(EnvironmentVariableModel).nullable)
        field("args", array(string).nullable)
        field("telemetryServiceName", string.nullable)
    }

    init {
        map("sessions", string, SessionModel)

        source("processStarted", ProcessStarted)
        source("processTerminated", ProcessTerminated)
        source("logReceived", LogReceived)

        sink("otelMetricReceived", MetricBase)
    }
}