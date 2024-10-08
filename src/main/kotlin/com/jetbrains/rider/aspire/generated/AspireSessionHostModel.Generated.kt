@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.jetbrains.rider.aspire.generated

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
    private val _createSession: RdCall<SessionModel, SessionCreationResult?>,
    private val _deleteSession: RdCall<String, Boolean>,
    private val _processStarted: RdSignal<ProcessStarted>,
    private val _processTerminated: RdSignal<ProcessTerminated>,
    private val _logReceived: RdSignal<LogReceived>,
    private val _resources: RdMap<String, ResourceWrapper>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-8012683471335252475), classLoader, "com.jetbrains.rider.aspire.generated.ProcessStarted"))
            serializers.register(LazyCompanionMarshaller(RdId(-4984966637681634785), classLoader, "com.jetbrains.rider.aspire.generated.ProcessTerminated"))
            serializers.register(LazyCompanionMarshaller(RdId(548077805281958706), classLoader, "com.jetbrains.rider.aspire.generated.LogReceived"))
            serializers.register(LazyCompanionMarshaller(RdId(-5369615389742325332), classLoader, "com.jetbrains.rider.aspire.generated.SessionEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(-1286323512761547290), classLoader, "com.jetbrains.rider.aspire.generated.SessionModel"))
            serializers.register(LazyCompanionMarshaller(RdId(-5594530824153105985), classLoader, "com.jetbrains.rider.aspire.generated.SessionCreationResult"))
            serializers.register(LazyCompanionMarshaller(RdId(-7695483574898099182), classLoader, "com.jetbrains.rider.aspire.generated.ResourceWrapper"))
            serializers.register(LazyCompanionMarshaller(RdId(-3770298982342277528), classLoader, "com.jetbrains.rider.aspire.generated.ResourceModel"))
            serializers.register(LazyCompanionMarshaller(RdId(1247681944195290678), classLoader, "com.jetbrains.rider.aspire.generated.ResourceProperty"))
            serializers.register(LazyCompanionMarshaller(RdId(-1423436662766610770), classLoader, "com.jetbrains.rider.aspire.generated.ResourceEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(552742225967993966), classLoader, "com.jetbrains.rider.aspire.generated.ResourceUrl"))
            serializers.register(LazyCompanionMarshaller(RdId(552742225967985219), classLoader, "com.jetbrains.rider.aspire.generated.ResourceLog"))
            serializers.register(LazyCompanionMarshaller(RdId(-1311735068701761509), classLoader, "com.jetbrains.rider.aspire.generated.ResourceType"))
            serializers.register(LazyCompanionMarshaller(RdId(-3770298982336589872), classLoader, "com.jetbrains.rider.aspire.generated.ResourceState"))
            serializers.register(LazyCompanionMarshaller(RdId(-15935776453165119), classLoader, "com.jetbrains.rider.aspire.generated.ResourceStateStyle"))
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
        
        private val __SessionCreationResultNullableSerializer = SessionCreationResult.nullable()
        
        const val serializationHash = -516885028222827508L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostModel
    override val serializationHash: Long get() = AspireSessionHostModel.serializationHash
    
    //fields
    val createSession: IRdEndpoint<SessionModel, SessionCreationResult?> get() = _createSession
    val deleteSession: IRdEndpoint<String, Boolean> get() = _deleteSession
    val processStarted: ISignal<ProcessStarted> get() = _processStarted
    val processTerminated: ISignal<ProcessTerminated> get() = _processTerminated
    val logReceived: ISignal<LogReceived> get() = _logReceived
    val resources: IMutableViewableMap<String, ResourceWrapper> get() = _resources
    //methods
    //initializer
    init {
        bindableChildren.add("createSession" to _createSession)
        bindableChildren.add("deleteSession" to _deleteSession)
        bindableChildren.add("processStarted" to _processStarted)
        bindableChildren.add("processTerminated" to _processTerminated)
        bindableChildren.add("logReceived" to _logReceived)
        bindableChildren.add("resources" to _resources)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdCall<SessionModel, SessionCreationResult?>(SessionModel, __SessionCreationResultNullableSerializer),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdSignal<ProcessStarted>(ProcessStarted),
        RdSignal<ProcessTerminated>(ProcessTerminated),
        RdSignal<LogReceived>(LogReceived),
        RdMap<String, ResourceWrapper>(FrameworkMarshallers.String, ResourceWrapper)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireSessionHostModel (")
        printer.indent {
            print("createSession = "); _createSession.print(printer); println()
            print("deleteSession = "); _deleteSession.print(printer); println()
            print("processStarted = "); _processStarted.print(printer); println()
            print("processTerminated = "); _processTerminated.print(printer); println()
            print("logReceived = "); _logReceived.print(printer); println()
            print("resources = "); _resources.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireSessionHostModel   {
        return AspireSessionHostModel(
            _createSession.deepClonePolymorphic(),
            _deleteSession.deepClonePolymorphic(),
            _processStarted.deepClonePolymorphic(),
            _processTerminated.deepClonePolymorphic(),
            _logReceived.deepClonePolymorphic(),
            _resources.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireSessionHostModel get() = getOrCreateExtension(AspireSessionHostModel::class) { @Suppress("DEPRECATION") AspireSessionHostModel.create(lifetime, this) }



/**
 * #### Generated from [AspireSessionHostModel.kt:27]
 */
data class LogReceived (
    val id: String,
    val isStdErr: Boolean,
    val message: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<LogReceived> {
        override val _type: KClass<LogReceived> = LogReceived::class
        override val id: RdId get() = RdId(548077805281958706)
        
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
 * #### Generated from [AspireSessionHostModel.kt:17]
 */
data class ProcessStarted (
    val id: String,
    val pid: Long
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ProcessStarted> {
        override val _type: KClass<ProcessStarted> = ProcessStarted::class
        override val id: RdId get() = RdId(-8012683471335252475)
        
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
 * #### Generated from [AspireSessionHostModel.kt:22]
 */
data class ProcessTerminated (
    val id: String,
    val exitCode: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ProcessTerminated> {
        override val _type: KClass<ProcessTerminated> = ProcessTerminated::class
        override val id: RdId get() = RdId(-4984966637681634785)
        
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
 * #### Generated from [AspireSessionHostModel.kt:95]
 */
data class ResourceEnvironmentVariable (
    val key: String,
    val value: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceEnvironmentVariable> {
        override val _type: KClass<ResourceEnvironmentVariable> = ResourceEnvironmentVariable::class
        override val id: RdId get() = RdId(-1423436662766610770)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceEnvironmentVariable  {
            val key = buffer.readString()
            val value = buffer.readNullable { buffer.readString() }
            return ResourceEnvironmentVariable(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceEnvironmentVariable)  {
            buffer.writeString(value.key)
            buffer.writeNullable(value.value) { buffer.writeString(it) }
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
        
        other as ResourceEnvironmentVariable
        
        if (key != other.key) return false
        if (value != other.value) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + key.hashCode()
        __r = __r*31 + if (value != null) value.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceEnvironmentVariable (")
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
 * #### Generated from [AspireSessionHostModel.kt:106]
 */
data class ResourceLog (
    val text: String,
    val isError: Boolean,
    val lineNumber: Int
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceLog> {
        override val _type: KClass<ResourceLog> = ResourceLog::class
        override val id: RdId get() = RdId(552742225967985219)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceLog  {
            val text = buffer.readString()
            val isError = buffer.readBool()
            val lineNumber = buffer.readInt()
            return ResourceLog(text, isError, lineNumber)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceLog)  {
            buffer.writeString(value.text)
            buffer.writeBool(value.isError)
            buffer.writeInt(value.lineNumber)
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
        
        other as ResourceLog
        
        if (text != other.text) return false
        if (isError != other.isError) return false
        if (lineNumber != other.lineNumber) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + text.hashCode()
        __r = __r*31 + isError.hashCode()
        __r = __r*31 + lineNumber.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceLog (")
        printer.indent {
            print("text = "); text.print(printer); println()
            print("isError = "); isError.print(printer); println()
            print("lineNumber = "); lineNumber.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:57]
 */
data class ResourceModel (
    val name: String,
    val type: ResourceType,
    val displayName: String,
    val uid: String,
    val state: ResourceState?,
    val stateStyle: ResourceStateStyle?,
    val createdAt: Date,
    val properties: Array<ResourceProperty>,
    val environment: Array<ResourceEnvironmentVariable>,
    val urls: Array<ResourceUrl>
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceModel> {
        override val _type: KClass<ResourceModel> = ResourceModel::class
        override val id: RdId get() = RdId(-3770298982342277528)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceModel  {
            val name = buffer.readString()
            val type = buffer.readEnum<ResourceType>()
            val displayName = buffer.readString()
            val uid = buffer.readString()
            val state = buffer.readNullable { buffer.readEnum<ResourceState>() }
            val stateStyle = buffer.readNullable { buffer.readEnum<ResourceStateStyle>() }
            val createdAt = buffer.readDateTime()
            val properties = buffer.readArray {ResourceProperty.read(ctx, buffer)}
            val environment = buffer.readArray {ResourceEnvironmentVariable.read(ctx, buffer)}
            val urls = buffer.readArray {ResourceUrl.read(ctx, buffer)}
            return ResourceModel(name, type, displayName, uid, state, stateStyle, createdAt, properties, environment, urls)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceModel)  {
            buffer.writeString(value.name)
            buffer.writeEnum(value.type)
            buffer.writeString(value.displayName)
            buffer.writeString(value.uid)
            buffer.writeNullable(value.state) { buffer.writeEnum(it) }
            buffer.writeNullable(value.stateStyle) { buffer.writeEnum(it) }
            buffer.writeDateTime(value.createdAt)
            buffer.writeArray(value.properties) { ResourceProperty.write(ctx, buffer, it) }
            buffer.writeArray(value.environment) { ResourceEnvironmentVariable.write(ctx, buffer, it) }
            buffer.writeArray(value.urls) { ResourceUrl.write(ctx, buffer, it) }
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
        
        other as ResourceModel
        
        if (name != other.name) return false
        if (type != other.type) return false
        if (displayName != other.displayName) return false
        if (uid != other.uid) return false
        if (state != other.state) return false
        if (stateStyle != other.stateStyle) return false
        if (createdAt != other.createdAt) return false
        if (!(properties contentDeepEquals other.properties)) return false
        if (!(environment contentDeepEquals other.environment)) return false
        if (!(urls contentDeepEquals other.urls)) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + type.hashCode()
        __r = __r*31 + displayName.hashCode()
        __r = __r*31 + uid.hashCode()
        __r = __r*31 + if (state != null) state.hashCode() else 0
        __r = __r*31 + if (stateStyle != null) stateStyle.hashCode() else 0
        __r = __r*31 + createdAt.hashCode()
        __r = __r*31 + properties.contentDeepHashCode()
        __r = __r*31 + environment.contentDeepHashCode()
        __r = __r*31 + urls.contentDeepHashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceModel (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("type = "); type.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
            print("uid = "); uid.print(printer); println()
            print("state = "); state.print(printer); println()
            print("stateStyle = "); stateStyle.print(printer); println()
            print("createdAt = "); createdAt.print(printer); println()
            print("properties = "); properties.print(printer); println()
            print("environment = "); environment.print(printer); println()
            print("urls = "); urls.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:89]
 */
data class ResourceProperty (
    val name: String,
    val displayName: String?,
    val value: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceProperty> {
        override val _type: KClass<ResourceProperty> = ResourceProperty::class
        override val id: RdId get() = RdId(1247681944195290678)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceProperty  {
            val name = buffer.readString()
            val displayName = buffer.readNullable { buffer.readString() }
            val value = buffer.readNullable { buffer.readString() }
            return ResourceProperty(name, displayName, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceProperty)  {
            buffer.writeString(value.name)
            buffer.writeNullable(value.displayName) { buffer.writeString(it) }
            buffer.writeNullable(value.value) { buffer.writeString(it) }
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
        
        other as ResourceProperty
        
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (value != other.value) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + if (displayName != null) displayName.hashCode() else 0
        __r = __r*31 + if (value != null) value.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceProperty (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
            print("value = "); value.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:67]
 */
enum class ResourceState {
    Finished, 
    Exited, 
    FailedToStart, 
    Starting, 
    Running, 
    Hidden, 
    Unknown;
    
    companion object : IMarshaller<ResourceState> {
        val marshaller = FrameworkMarshallers.enum<ResourceState>()
        
        
        override val _type: KClass<ResourceState> = ResourceState::class
        override val id: RdId get() = RdId(-3770298982336589872)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceState {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceState)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:76]
 */
enum class ResourceStateStyle {
    Success, 
    Info, 
    Warning, 
    Error, 
    Unknown;
    
    companion object : IMarshaller<ResourceStateStyle> {
        val marshaller = FrameworkMarshallers.enum<ResourceStateStyle>()
        
        
        override val _type: KClass<ResourceStateStyle> = ResourceStateStyle::class
        override val id: RdId get() = RdId(-15935776453165119)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceStateStyle {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceStateStyle)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:59]
 */
enum class ResourceType {
    Project, 
    Container, 
    Executable, 
    Unknown;
    
    companion object : IMarshaller<ResourceType> {
        val marshaller = FrameworkMarshallers.enum<ResourceType>()
        
        
        override val _type: KClass<ResourceType> = ResourceType::class
        override val id: RdId get() = RdId(-1311735068701761509)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceType {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceType)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:100]
 */
data class ResourceUrl (
    val name: String,
    val fullUrl: String,
    val isInternal: Boolean
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceUrl> {
        override val _type: KClass<ResourceUrl> = ResourceUrl::class
        override val id: RdId get() = RdId(552742225967993966)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceUrl  {
            val name = buffer.readString()
            val fullUrl = buffer.readString()
            val isInternal = buffer.readBool()
            return ResourceUrl(name, fullUrl, isInternal)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceUrl)  {
            buffer.writeString(value.name)
            buffer.writeString(value.fullUrl)
            buffer.writeBool(value.isInternal)
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
        
        other as ResourceUrl
        
        if (name != other.name) return false
        if (fullUrl != other.fullUrl) return false
        if (isInternal != other.isInternal) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + fullUrl.hashCode()
        __r = __r*31 + isInternal.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceUrl (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("fullUrl = "); fullUrl.print(printer); println()
            print("isInternal = "); isInternal.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:51]
 */
class ResourceWrapper private constructor(
    private val _model: RdOptionalProperty<ResourceModel>,
    private val _isInitialized: RdOptionalProperty<Boolean>,
    private val _logReceived: RdSignal<ResourceLog>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<ResourceWrapper> {
        override val _type: KClass<ResourceWrapper> = ResourceWrapper::class
        override val id: RdId get() = RdId(-7695483574898099182)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceWrapper  {
            val _id = RdId.read(buffer)
            val _model = RdOptionalProperty.read(ctx, buffer, ResourceModel)
            val _isInitialized = RdOptionalProperty.read(ctx, buffer, FrameworkMarshallers.Bool)
            val _logReceived = RdSignal.read(ctx, buffer, ResourceLog)
            return ResourceWrapper(_model, _isInitialized, _logReceived).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceWrapper)  {
            value.rdid.write(buffer)
            RdOptionalProperty.write(ctx, buffer, value._model)
            RdOptionalProperty.write(ctx, buffer, value._isInitialized)
            RdSignal.write(ctx, buffer, value._logReceived)
        }
        
        
    }
    //fields
    val model: IOptProperty<ResourceModel> get() = _model
    val isInitialized: IOptProperty<Boolean> get() = _isInitialized
    val logReceived: ISource<ResourceLog> get() = _logReceived
    //methods
    //initializer
    init {
        _model.optimizeNested = true
        _isInitialized.optimizeNested = true
    }
    
    init {
        _isInitialized.async = true
    }
    
    init {
        bindableChildren.add("model" to _model)
        bindableChildren.add("isInitialized" to _isInitialized)
        bindableChildren.add("logReceived" to _logReceived)
    }
    
    //secondary constructor
    constructor(
    ) : this(
        RdOptionalProperty<ResourceModel>(ResourceModel),
        RdOptionalProperty<Boolean>(FrameworkMarshallers.Bool),
        RdSignal<ResourceLog>(ResourceLog)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceWrapper (")
        printer.indent {
            print("model = "); _model.print(printer); println()
            print("isInitialized = "); _isInitialized.print(printer); println()
            print("logReceived = "); _logReceived.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): ResourceWrapper   {
        return ResourceWrapper(
            _model.deepClonePolymorphic(),
            _isInitialized.deepClonePolymorphic(),
            _logReceived.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:47]
 */
data class SessionCreationResult (
    val sessionId: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SessionCreationResult> {
        override val _type: KClass<SessionCreationResult> = SessionCreationResult::class
        override val id: RdId get() = RdId(-5594530824153105985)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionCreationResult  {
            val sessionId = buffer.readString()
            return SessionCreationResult(sessionId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionCreationResult)  {
            buffer.writeString(value.sessionId)
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
        
        other as SessionCreationResult
        
        if (sessionId != other.sessionId) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + sessionId.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SessionCreationResult (")
        printer.indent {
            print("sessionId = "); sessionId.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:33]
 */
data class SessionEnvironmentVariable (
    val key: String,
    val value: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SessionEnvironmentVariable> {
        override val _type: KClass<SessionEnvironmentVariable> = SessionEnvironmentVariable::class
        override val id: RdId get() = RdId(-5369615389742325332)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionEnvironmentVariable  {
            val key = buffer.readString()
            val value = buffer.readString()
            return SessionEnvironmentVariable(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionEnvironmentVariable)  {
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
        
        other as SessionEnvironmentVariable
        
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
        printer.println("SessionEnvironmentVariable (")
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
 * #### Generated from [AspireSessionHostModel.kt:38]
 */
data class SessionModel (
    val projectPath: String,
    val debug: Boolean,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: Array<String>?,
    val envs: Array<SessionEnvironmentVariable>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SessionModel> {
        override val _type: KClass<SessionModel> = SessionModel::class
        override val id: RdId get() = RdId(-1286323512761547290)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionModel  {
            val projectPath = buffer.readString()
            val debug = buffer.readBool()
            val launchProfile = buffer.readNullable { buffer.readString() }
            val disableLaunchProfile = buffer.readBool()
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val envs = buffer.readNullable { buffer.readArray {SessionEnvironmentVariable.read(ctx, buffer)} }
            return SessionModel(projectPath, debug, launchProfile, disableLaunchProfile, args, envs)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionModel)  {
            buffer.writeString(value.projectPath)
            buffer.writeBool(value.debug)
            buffer.writeNullable(value.launchProfile) { buffer.writeString(it) }
            buffer.writeBool(value.disableLaunchProfile)
            buffer.writeNullable(value.args) { buffer.writeArray(it) { buffer.writeString(it) } }
            buffer.writeNullable(value.envs) { buffer.writeArray(it) { SessionEnvironmentVariable.write(ctx, buffer, it) } }
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
        
        other as SessionModel
        
        if (projectPath != other.projectPath) return false
        if (debug != other.debug) return false
        if (launchProfile != other.launchProfile) return false
        if (disableLaunchProfile != other.disableLaunchProfile) return false
        if (args != other.args) return false
        if (envs != other.envs) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + projectPath.hashCode()
        __r = __r*31 + debug.hashCode()
        __r = __r*31 + if (launchProfile != null) launchProfile.hashCode() else 0
        __r = __r*31 + disableLaunchProfile.hashCode()
        __r = __r*31 + if (args != null) args.contentDeepHashCode() else 0
        __r = __r*31 + if (envs != null) envs.contentDeepHashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SessionModel (")
        printer.indent {
            print("projectPath = "); projectPath.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("launchProfile = "); launchProfile.print(printer); println()
            print("disableLaunchProfile = "); disableLaunchProfile.print(printer); println()
            print("args = "); args.print(printer); println()
            print("envs = "); envs.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
