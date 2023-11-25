package model.sessionHost

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator

object AspireSessionHostRoot : Root() {
    init {
        setting(Kotlin11Generator.Namespace, "com.github.rafaelldi.aspireplugin.generated")
        setting(CSharp50Generator.Namespace, "AspireSessionHost.Generated")
    }
}

@Suppress("unused")
object AspireSessionHostModel : Ext(AspireSessionHostRoot) {
    private val EnvironmentVariableModel = structdef {
        field("key", string)
        field("value", string)
    }

    private val SessionModel = structdef {
        field("projectPath", string)
        field("debug", bool)
        field("envs", array(EnvironmentVariableModel).nullable)
        field("args", array(string).nullable)
    }

    init {
        map("sessions", string, SessionModel)
    }
}