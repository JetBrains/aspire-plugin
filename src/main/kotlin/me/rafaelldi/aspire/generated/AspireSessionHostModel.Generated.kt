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
 * #### Generated from [AspireSessionHostModel.kt:16]
 */
class AspireSessionHostModel private constructor(
    private val _sessions: RdMap<String, SessionModel>,
    private val _processStarted: RdSignal<ProcessStarted>,
    private val _processTerminated: RdSignal<ProcessTerminated>,
    private val _logReceived: RdSignal<LogReceived>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            serializers.register(EnvironmentVariableModel)
            serializers.register(ProcessStarted)
            serializers.register(ProcessTerminated)
            serializers.register(LogReceived)
            serializers.register(MetricKey)
            serializers.register(MetricValue)
            serializers.register(SessionModel)
            serializers.register(TraceNode)
            serializers.register(TraceNodeAttribute)
            serializers.register(TraceNodeChild)
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): AspireSessionHostModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.aspireSessionHostModel or revise the extension scope instead", ReplaceWith("protocol.aspireSessionHostModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): AspireSessionHostModel  {
            AspireSessionHostRoot.register(protocol.serializers)
            
            return AspireSessionHostModel()
        }
        
        
        const val serializationHash = -7617180274363272515L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostModel
    override val serializationHash: Long get() = AspireSessionHostModel.serializationHash
    
    //fields
    val sessions: IMutableViewableMap<String, SessionModel> get() = _sessions
    val processStarted: ISignal<ProcessStarted> get() = _processStarted
    val processTerminated: ISignal<ProcessTerminated> get() = _processTerminated
    val logReceived: ISignal<LogReceived> get() = _logReceived
    //methods
    //initializer
    init {
        bindableChildren.add("sessions" to _sessions)
        bindableChildren.add("processStarted" to _processStarted)
        bindableChildren.add("processTerminated" to _processTerminated)
        bindableChildren.add("logReceived" to _logReceived)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdMap<String, SessionModel>(FrameworkMarshallers.String, SessionModel),
        RdSignal<ProcessStarted>(ProcessStarted),
        RdSignal<ProcessTerminated>(ProcessTerminated),
        RdSignal<LogReceived>(LogReceived)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireSessionHostModel (")
        printer.indent {
            print("sessions = "); _sessions.print(printer); println()
            print("processStarted = "); _processStarted.print(printer); println()
            print("processTerminated = "); _processTerminated.print(printer); println()
            print("logReceived = "); _logReceived.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireSessionHostModel   {
        return AspireSessionHostModel(
            _sessions.deepClonePolymorphic(),
            _processStarted.deepClonePolymorphic(),
            _processTerminated.deepClonePolymorphic(),
            _logReceived.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireSessionHostModel get() = getOrCreateExtension(AspireSessionHostModel::class) { @Suppress("DEPRECATION") AspireSessionHostModel.create(lifetime, this) }



/**
 * #### Generated from [AspireSessionHostModel.kt:17]
 */
data class EnvironmentVariableModel (
    val key: String,
    val value: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<EnvironmentVariableModel> {
        override val _type: KClass<EnvironmentVariableModel> = EnvironmentVariableModel::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): EnvironmentVariableModel  {
            val key = buffer.readString()
            val value = buffer.readString()
            return EnvironmentVariableModel(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: EnvironmentVariableModel)  {
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
        
        other as EnvironmentVariableModel
        
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
        printer.println("EnvironmentVariableModel (")
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
 * #### Generated from [AspireSessionHostModel.kt:32]
 */
data class LogReceived (
    val id: String,
    val isStdErr: Boolean,
    val message: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LogReceived> {
        override val _type: KClass<LogReceived> = LogReceived::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): LogReceived  {
            val id = buffer.readString()
            val isStdErr = buffer.readBool()
            val message = buffer.readString()
            return LogReceived(id, isStdErr, message)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: LogReceived)  {
            buffer.writeString(value.id)
            buffer.writeBool(value.isStdErr)
            buffer.writeString(value.message)
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
        
        other as LogReceived
        
        if (id != other.id) return false
        if (isStdErr != other.isStdErr) return false
        if (message != other.message) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + isStdErr.hashCode()
        __r = __r*31 + message.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("LogReceived (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("isStdErr = "); isStdErr.print(printer); println()
            print("message = "); message.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:38]
 */
data class MetricKey (
    val scope: String,
    val name: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<MetricKey> {
        override val _type: KClass<MetricKey> = MetricKey::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): MetricKey  {
            val scope = buffer.readString()
            val name = buffer.readString()
            return MetricKey(scope, name)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: MetricKey)  {
            buffer.writeString(value.scope)
            buffer.writeString(value.name)
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
        
        other as MetricKey
        
        if (scope != other.scope) return false
        if (name != other.name) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + scope.hashCode()
        __r = __r*31 + name.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("MetricKey (")
        printer.indent {
            print("scope = "); scope.print(printer); println()
            print("name = "); name.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:43]
 */
data class MetricValue (
    val serviceName: String,
    val scope: String,
    val name: String,
    val description: String?,
    val unit: String?,
    val value: Double,
    val timestamp: Long
) : IPrintable {
    //companion
    
    companion object : IMarshaller<MetricValue> {
        override val _type: KClass<MetricValue> = MetricValue::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): MetricValue  {
            val serviceName = buffer.readString()
            val scope = buffer.readString()
            val name = buffer.readString()
            val description = buffer.readNullable { buffer.readString() }
            val unit = buffer.readNullable { buffer.readString() }
            val value = buffer.readDouble()
            val timestamp = buffer.readLong()
            return MetricValue(serviceName, scope, name, description, unit, value, timestamp)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: MetricValue)  {
            buffer.writeString(value.serviceName)
            buffer.writeString(value.scope)
            buffer.writeString(value.name)
            buffer.writeNullable(value.description) { buffer.writeString(it) }
            buffer.writeNullable(value.unit) { buffer.writeString(it) }
            buffer.writeDouble(value.value)
            buffer.writeLong(value.timestamp)
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
        
        other as MetricValue
        
        if (serviceName != other.serviceName) return false
        if (scope != other.scope) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (unit != other.unit) return false
        if (value != other.value) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + serviceName.hashCode()
        __r = __r*31 + scope.hashCode()
        __r = __r*31 + name.hashCode()
        __r = __r*31 + if (description != null) description.hashCode() else 0
        __r = __r*31 + if (unit != null) unit.hashCode() else 0
        __r = __r*31 + value.hashCode()
        __r = __r*31 + timestamp.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("MetricValue (")
        printer.indent {
            print("serviceName = "); serviceName.print(printer); println()
            print("scope = "); scope.print(printer); println()
            print("name = "); name.print(printer); println()
            print("description = "); description.print(printer); println()
            print("unit = "); unit.print(printer); println()
            print("value = "); value.print(printer); println()
            print("timestamp = "); timestamp.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:22]
 */
data class ProcessStarted (
    val id: String,
    val pid: Long
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ProcessStarted> {
        override val _type: KClass<ProcessStarted> = ProcessStarted::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ProcessStarted  {
            val id = buffer.readString()
            val pid = buffer.readLong()
            return ProcessStarted(id, pid)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ProcessStarted)  {
            buffer.writeString(value.id)
            buffer.writeLong(value.pid)
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
        
        other as ProcessStarted
        
        if (id != other.id) return false
        if (pid != other.pid) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + pid.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ProcessStarted (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("pid = "); pid.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:27]
 */
data class ProcessTerminated (
    val id: String,
    val exitCode: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ProcessTerminated> {
        override val _type: KClass<ProcessTerminated> = ProcessTerminated::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ProcessTerminated  {
            val id = buffer.readString()
            val exitCode = buffer.readInt()
            return ProcessTerminated(id, exitCode)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ProcessTerminated)  {
            buffer.writeString(value.id)
            buffer.writeInt(value.exitCode)
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
        
        other as ProcessTerminated
        
        if (id != other.id) return false
        if (exitCode != other.exitCode) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + exitCode.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ProcessTerminated (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("exitCode = "); exitCode.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:53]
 */
class SessionModel private constructor(
    val id: String,
    val projectPath: String,
    val debug: Boolean,
    val envs: Array<EnvironmentVariableModel>?,
    val args: Array<String>?,
    val telemetryServiceName: String?,
    private val _metrics: RdMap<MetricKey, MetricValue>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<SessionModel> {
        override val _type: KClass<SessionModel> = SessionModel::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionModel  {
            val _id = RdId.read(buffer)
            val id = buffer.readString()
            val projectPath = buffer.readString()
            val debug = buffer.readBool()
            val envs = buffer.readNullable { buffer.readArray {EnvironmentVariableModel.read(ctx, buffer)} }
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val telemetryServiceName = buffer.readNullable { buffer.readString() }
            val _metrics = RdMap.read(ctx, buffer, MetricKey, MetricValue)
            return SessionModel(id, projectPath, debug, envs, args, telemetryServiceName, _metrics).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionModel)  {
            value.rdid.write(buffer)
            buffer.writeString(value.id)
            buffer.writeString(value.projectPath)
            buffer.writeBool(value.debug)
            buffer.writeNullable(value.envs) { buffer.writeArray(it) { EnvironmentVariableModel.write(ctx, buffer, it) } }
            buffer.writeNullable(value.args) { buffer.writeArray(it) { buffer.writeString(it) } }
            buffer.writeNullable(value.telemetryServiceName) { buffer.writeString(it) }
            RdMap.write(ctx, buffer, value._metrics)
        }
        
        
    }
    //fields
    val metrics: IMutableViewableMap<MetricKey, MetricValue> get() = _metrics
    //methods
    //initializer
    init {
        _metrics.optimizeNested = true
    }
    
    init {
        _metrics.async = true
    }
    
    init {
        bindableChildren.add("metrics" to _metrics)
    }
    
    //secondary constructor
    constructor(
        id: String,
        projectPath: String,
        debug: Boolean,
        envs: Array<EnvironmentVariableModel>?,
        args: Array<String>?,
        telemetryServiceName: String?
    ) : this(
        id,
        projectPath,
        debug,
        envs,
        args,
        telemetryServiceName,
        RdMap<MetricKey, MetricValue>(MetricKey, MetricValue)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SessionModel (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("projectPath = "); projectPath.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("envs = "); envs.print(printer); println()
            print("args = "); args.print(printer); println()
            print("telemetryServiceName = "); telemetryServiceName.print(printer); println()
            print("metrics = "); _metrics.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): SessionModel   {
        return SessionModel(
            id,
            projectPath,
            debug,
            envs,
            args,
            telemetryServiceName,
            _metrics.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:63]
 */
data class TraceNode (
    val id: String,
    val serviceName: String?,
    val displayName: String,
    val attributes: List<TraceNodeAttribute>,
    val children: List<TraceNodeChild>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<TraceNode> {
        override val _type: KClass<TraceNode> = TraceNode::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): TraceNode  {
            val id = buffer.readString()
            val serviceName = buffer.readNullable { buffer.readString() }
            val displayName = buffer.readString()
            val attributes = buffer.readList { TraceNodeAttribute.read(ctx, buffer) }
            val children = buffer.readList { TraceNodeChild.read(ctx, buffer) }
            return TraceNode(id, serviceName, displayName, attributes, children)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: TraceNode)  {
            buffer.writeString(value.id)
            buffer.writeNullable(value.serviceName) { buffer.writeString(it) }
            buffer.writeString(value.displayName)
            buffer.writeList(value.attributes) { v -> TraceNodeAttribute.write(ctx, buffer, v) }
            buffer.writeList(value.children) { v -> TraceNodeChild.write(ctx, buffer, v) }
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
        
        other as TraceNode
        
        if (id != other.id) return false
        if (serviceName != other.serviceName) return false
        if (displayName != other.displayName) return false
        if (attributes != other.attributes) return false
        if (children != other.children) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + if (serviceName != null) serviceName.hashCode() else 0
        __r = __r*31 + displayName.hashCode()
        __r = __r*31 + attributes.hashCode()
        __r = __r*31 + children.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("TraceNode (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("serviceName = "); serviceName.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
            print("attributes = "); attributes.print(printer); println()
            print("children = "); children.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:71]
 */
data class TraceNodeAttribute (
    val key: String,
    val value: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<TraceNodeAttribute> {
        override val _type: KClass<TraceNodeAttribute> = TraceNodeAttribute::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): TraceNodeAttribute  {
            val key = buffer.readString()
            val value = buffer.readString()
            return TraceNodeAttribute(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: TraceNodeAttribute)  {
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
        
        other as TraceNodeAttribute
        
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
        printer.println("TraceNodeAttribute (")
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
 * #### Generated from [AspireSessionHostModel.kt:76]
 */
data class TraceNodeChild (
    val id: String,
    val connectionCount: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<TraceNodeChild> {
        override val _type: KClass<TraceNodeChild> = TraceNodeChild::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): TraceNodeChild  {
            val id = buffer.readString()
            val connectionCount = buffer.readInt()
            return TraceNodeChild(id, connectionCount)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: TraceNodeChild)  {
            buffer.writeString(value.id)
            buffer.writeInt(value.connectionCount)
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
        
        other as TraceNodeChild
        
        if (id != other.id) return false
        if (connectionCount != other.connectionCount) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + connectionCount.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("TraceNodeChild (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("connectionCount = "); connectionCount.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
