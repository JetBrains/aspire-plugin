@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package me.rafaelldi.aspire.generated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import com.jetbrains.rd.util.*
import kotlin.time.Duration
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [AspirePluginModel.kt:10]
 */
class AspirePluginModel private constructor(
    private val _startSessionHost: RdCall<StartSessionHostRequest, StartSessionHostResponse>,
    private val _stopSessionHost: RdCall<StopSessionHostRequest, Unit>,
    private val _unitTestRunCancelled: RdSignal<String>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(4350297137280618944), classLoader, "me.rafaelldi.aspire.generated.StartSessionHostRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(5543058198174973028), classLoader, "me.rafaelldi.aspire.generated.SessionHostEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(5732002739785259824), classLoader, "me.rafaelldi.aspire.generated.StartSessionHostResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-51507571507542298), classLoader, "me.rafaelldi.aspire.generated.StopSessionHostRequest"))
        }
        
        
        
        
        
        const val serializationHash = 3962467493622399682L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspirePluginModel
    override val serializationHash: Long get() = AspirePluginModel.serializationHash
    
    //fields
    val startSessionHost: IRdEndpoint<StartSessionHostRequest, StartSessionHostResponse> get() = _startSessionHost
    val stopSessionHost: IRdEndpoint<StopSessionHostRequest, Unit> get() = _stopSessionHost
    val unitTestRunCancelled: IAsyncSource<String> get() = _unitTestRunCancelled
    //methods
    //initializer
    init {
        _startSessionHost.async = true
        _stopSessionHost.async = true
        _unitTestRunCancelled.async = true
    }
    
    init {
        bindableChildren.add("startSessionHost" to _startSessionHost)
        bindableChildren.add("stopSessionHost" to _stopSessionHost)
        bindableChildren.add("unitTestRunCancelled" to _unitTestRunCancelled)
    }
    
    //secondary constructor
    internal constructor(
    ) : this(
        RdCall<StartSessionHostRequest, StartSessionHostResponse>(StartSessionHostRequest, StartSessionHostResponse),
        RdCall<StopSessionHostRequest, Unit>(StopSessionHostRequest, FrameworkMarshallers.Void),
        RdSignal<String>(FrameworkMarshallers.String)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspirePluginModel (")
        printer.indent {
            print("startSessionHost = "); _startSessionHost.print(printer); println()
            print("stopSessionHost = "); _stopSessionHost.print(printer); println()
            print("unitTestRunCancelled = "); _unitTestRunCancelled.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspirePluginModel   {
        return AspirePluginModel(
            _startSessionHost.deepClonePolymorphic(),
            _stopSessionHost.deepClonePolymorphic(),
            _unitTestRunCancelled.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val com.jetbrains.rd.ide.model.Solution.aspirePluginModel get() = getOrCreateExtension("aspirePluginModel", ::AspirePluginModel)



/**
 * #### Generated from [AspirePluginModel.kt:17]
 */
data class SessionHostEnvironmentVariable (
    val key: String,
    val value: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SessionHostEnvironmentVariable> {
        override val _type: KClass<SessionHostEnvironmentVariable> = SessionHostEnvironmentVariable::class
        override val id: RdId get() = RdId(5543058198174973028)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionHostEnvironmentVariable  {
            val key = buffer.readString()
            val value = buffer.readString()
            return SessionHostEnvironmentVariable(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionHostEnvironmentVariable)  {
            buffer.writeString(value.key)
            buffer.writeString(value.value)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as SessionHostEnvironmentVariable
        
        if (key != other.key) return false
        if (value != other.value) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + key.hashCode()
        __r = __r*31 + value.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SessionHostEnvironmentVariable (")
        printer.indent {
            print("key = "); key.print(printer); println()
            print("value = "); value.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:11]
 */
data class StartSessionHostRequest (
    val unitTestRunId: String,
    val aspireHostProjectPath: String,
    val underDebugger: Boolean
) : IPrintable {
    //companion
    
    companion object : IMarshaller<StartSessionHostRequest> {
        override val _type: KClass<StartSessionHostRequest> = StartSessionHostRequest::class
        override val id: RdId get() = RdId(4350297137280618944)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StartSessionHostRequest  {
            val unitTestRunId = buffer.readString()
            val aspireHostProjectPath = buffer.readString()
            val underDebugger = buffer.readBool()
            return StartSessionHostRequest(unitTestRunId, aspireHostProjectPath, underDebugger)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StartSessionHostRequest)  {
            buffer.writeString(value.unitTestRunId)
            buffer.writeString(value.aspireHostProjectPath)
            buffer.writeBool(value.underDebugger)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as StartSessionHostRequest
        
        if (unitTestRunId != other.unitTestRunId) return false
        if (aspireHostProjectPath != other.aspireHostProjectPath) return false
        if (underDebugger != other.underDebugger) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + unitTestRunId.hashCode()
        __r = __r*31 + aspireHostProjectPath.hashCode()
        __r = __r*31 + underDebugger.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("StartSessionHostRequest (")
        printer.indent {
            print("unitTestRunId = "); unitTestRunId.print(printer); println()
            print("aspireHostProjectPath = "); aspireHostProjectPath.print(printer); println()
            print("underDebugger = "); underDebugger.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:22]
 */
data class StartSessionHostResponse (
    val environmentVariables: Array<SessionHostEnvironmentVariable>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<StartSessionHostResponse> {
        override val _type: KClass<StartSessionHostResponse> = StartSessionHostResponse::class
        override val id: RdId get() = RdId(5732002739785259824)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StartSessionHostResponse  {
            val environmentVariables = buffer.readArray {SessionHostEnvironmentVariable.read(ctx, buffer)}
            return StartSessionHostResponse(environmentVariables)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StartSessionHostResponse)  {
            buffer.writeArray(value.environmentVariables) { SessionHostEnvironmentVariable.write(ctx, buffer, it) }
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as StartSessionHostResponse
        
        if (!(environmentVariables contentDeepEquals other.environmentVariables)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + environmentVariables.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("StartSessionHostResponse (")
        printer.indent {
            print("environmentVariables = "); environmentVariables.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:26]
 */
data class StopSessionHostRequest (
    val unitTestRunId: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<StopSessionHostRequest> {
        override val _type: KClass<StopSessionHostRequest> = StopSessionHostRequest::class
        override val id: RdId get() = RdId(-51507571507542298)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StopSessionHostRequest  {
            val unitTestRunId = buffer.readString()
            return StopSessionHostRequest(unitTestRunId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StopSessionHostRequest)  {
            buffer.writeString(value.unitTestRunId)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    override fun equals(other: Any?): Boolean  {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        
        other as StopSessionHostRequest
        
        if (unitTestRunId != other.unitTestRunId) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + unitTestRunId.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("StopSessionHostRequest (")
        printer.indent {
            print("unitTestRunId = "); unitTestRunId.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
