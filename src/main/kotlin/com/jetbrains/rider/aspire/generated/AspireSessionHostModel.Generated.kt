@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.jetbrains.rider.aspire.generated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.RdCall
import com.jetbrains.rd.framework.impl.RdMap
import com.jetbrains.rd.framework.impl.RdOptionalProperty
import com.jetbrains.rd.framework.impl.RdSignal
import com.jetbrains.rd.util.Date
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IMutableViewableMap
import com.jetbrains.rd.util.reactive.IOptProperty
import com.jetbrains.rd.util.reactive.ISignal
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.string.IPrintable
import com.jetbrains.rd.util.string.PrettyPrinter
import com.jetbrains.rd.util.string.print
import kotlin.reflect.KClass


/**
 * #### Generated from [AspireSessionHostModel.kt:16]
 */
class AspireSessionHostModel private constructor(
    private val _createSession: RdCall<SessionModel, SessionCreationResult?>,
    private val _deleteSession: RdCall<String, Boolean>,
    private val _processStarted: RdSignal<ProcessStarted>,
    private val _processTerminated: RdSignal<ProcessTerminated>,
    private val _logReceived: RdSignal<LogReceived>,
    private val _aspireHosts: RdMap<String, AspireHostModel>
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
            serializers.register(LazyCompanionMarshaller(RdId(-6198804010095377477), classLoader, "com.jetbrains.rider.aspire.generated.ResourceVolume"))
            serializers.register(LazyCompanionMarshaller(RdId(2840668839301507407), classLoader, "com.jetbrains.rider.aspire.generated.ResourceHealthStatus"))
            serializers.register(LazyCompanionMarshaller(RdId(2840668839259467409), classLoader, "com.jetbrains.rider.aspire.generated.ResourceHealthReport"))
            serializers.register(LazyCompanionMarshaller(RdId(-7695483592723081526), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommand"))
            serializers.register(LazyCompanionMarshaller(RdId(552742225967985219), classLoader, "com.jetbrains.rider.aspire.generated.ResourceLog"))
            serializers.register(LazyCompanionMarshaller(RdId(-3460782994127365019), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(3396191624361927979), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(7139185059374037243), classLoader, "com.jetbrains.rider.aspire.generated.AspireHostConfig"))
            serializers.register(LazyCompanionMarshaller(RdId(7370971417554020944), classLoader, "com.jetbrains.rider.aspire.generated.AspireHostModel"))
            serializers.register(LazyCompanionMarshaller(RdId(-1311735068701761509), classLoader, "com.jetbrains.rider.aspire.generated.ResourceType"))
            serializers.register(LazyCompanionMarshaller(RdId(-3770298982336589872), classLoader, "com.jetbrains.rider.aspire.generated.ResourceState"))
            serializers.register(LazyCompanionMarshaller(RdId(-15935776453165119), classLoader, "com.jetbrains.rider.aspire.generated.ResourceStateStyle"))
            serializers.register(LazyCompanionMarshaller(RdId(2722140349088377831), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandState"))
            serializers.register(LazyCompanionMarshaller(RdId(-8716242335550732449), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandResponseKind"))
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
        
        const val serializationHash = -1593802969616715494L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostModel
    override val serializationHash: Long get() = AspireSessionHostModel.serializationHash
    
    //fields
    val createSession: IRdEndpoint<SessionModel, SessionCreationResult?> get() = _createSession
    val deleteSession: IRdEndpoint<String, Boolean> get() = _deleteSession
    val processStarted: ISignal<ProcessStarted> get() = _processStarted
    val processTerminated: ISignal<ProcessTerminated> get() = _processTerminated
    val logReceived: ISignal<LogReceived> get() = _logReceived
    val aspireHosts: IMutableViewableMap<String, AspireHostModel> get() = _aspireHosts
    //methods
    //initializer
    init {
        bindableChildren.add("createSession" to _createSession)
        bindableChildren.add("deleteSession" to _deleteSession)
        bindableChildren.add("processStarted" to _processStarted)
        bindableChildren.add("processTerminated" to _processTerminated)
        bindableChildren.add("logReceived" to _logReceived)
        bindableChildren.add("aspireHosts" to _aspireHosts)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdCall<SessionModel, SessionCreationResult?>(SessionModel, __SessionCreationResultNullableSerializer),
        RdCall<String, Boolean>(FrameworkMarshallers.String, FrameworkMarshallers.Bool),
        RdSignal<ProcessStarted>(ProcessStarted),
        RdSignal<ProcessTerminated>(ProcessTerminated),
        RdSignal<LogReceived>(LogReceived),
        RdMap<String, AspireHostModel>(FrameworkMarshallers.String, AspireHostModel)
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
            print("aspireHosts = "); _aspireHosts.print(printer); println()
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
            _aspireHosts.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireSessionHostModel get() = getOrCreateExtension(AspireSessionHostModel::class) { @Suppress("DEPRECATION") AspireSessionHostModel.create(lifetime, this) }



/**
 * #### Generated from [AspireSessionHostModel.kt:170]
 */
data class AspireHostConfig (
    val resourceServiceEndpointUrl: String?,
    val resourceServiceApiKey: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<AspireHostConfig> {
        override val _type: KClass<AspireHostConfig> = AspireHostConfig::class
        override val id: RdId get() = RdId(7139185059374037243)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostConfig  {
            val resourceServiceEndpointUrl = buffer.readNullable { buffer.readString() }
            val resourceServiceApiKey = buffer.readNullable { buffer.readString() }
            return AspireHostConfig(resourceServiceEndpointUrl, resourceServiceApiKey)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostConfig)  {
            buffer.writeNullable(value.resourceServiceEndpointUrl) { buffer.writeString(it) }
            buffer.writeNullable(value.resourceServiceApiKey) { buffer.writeString(it) }
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
        
        other as AspireHostConfig
        
        if (resourceServiceEndpointUrl != other.resourceServiceEndpointUrl) return false
        if (resourceServiceApiKey != other.resourceServiceApiKey) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (resourceServiceEndpointUrl != null) resourceServiceEndpointUrl.hashCode() else 0
        __r = __r*31 + if (resourceServiceApiKey != null) resourceServiceApiKey.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostConfig (")
        printer.indent {
            print("resourceServiceEndpointUrl = "); resourceServiceEndpointUrl.print(printer); println()
            print("resourceServiceApiKey = "); resourceServiceApiKey.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:175]
 */
class AspireHostModel private constructor(
    val config: AspireHostConfig,
    private val _resources: RdMap<String, ResourceWrapper>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<AspireHostModel> {
        override val _type: KClass<AspireHostModel> = AspireHostModel::class
        override val id: RdId get() = RdId(7370971417554020944)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostModel  {
            val _id = RdId.read(buffer)
            val config = AspireHostConfig.read(ctx, buffer)
            val _resources = RdMap.read(ctx, buffer, FrameworkMarshallers.String, ResourceWrapper)
            return AspireHostModel(config, _resources).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostModel)  {
            value.rdid.write(buffer)
            AspireHostConfig.write(ctx, buffer, value.config)
            RdMap.write(ctx, buffer, value._resources)
        }
        
        
    }
    //fields
    val resources: IMutableViewableMap<String, ResourceWrapper> get() = _resources
    //methods
    //initializer
    init {
        bindableChildren.add("resources" to _resources)
    }
    
    //secondary constructor
    constructor(
        config: AspireHostConfig
    ) : this(
        config,
        RdMap<String, ResourceWrapper>(FrameworkMarshallers.String, ResourceWrapper)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostModel (")
        printer.indent {
            print("config = "); config.print(printer); println()
            print("resources = "); _resources.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireHostModel   {
        return AspireHostModel(
            config,
            _resources.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


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
 * #### Generated from [AspireSessionHostModel.kt:134]
 */
data class ResourceCommand (
    val commandType: String,
    val displayName: String,
    val confirmationMessage: String?,
    val isHighlighted: Boolean,
    val iconName: String?,
    val displayDescription: String?,
    val state: ResourceCommandState
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceCommand> {
        override val _type: KClass<ResourceCommand> = ResourceCommand::class
        override val id: RdId get() = RdId(-7695483592723081526)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommand  {
            val commandType = buffer.readString()
            val displayName = buffer.readString()
            val confirmationMessage = buffer.readNullable { buffer.readString() }
            val isHighlighted = buffer.readBool()
            val iconName = buffer.readNullable { buffer.readString() }
            val displayDescription = buffer.readNullable { buffer.readString() }
            val state = buffer.readEnum<ResourceCommandState>()
            return ResourceCommand(commandType, displayName, confirmationMessage, isHighlighted, iconName, displayDescription, state)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommand)  {
            buffer.writeString(value.commandType)
            buffer.writeString(value.displayName)
            buffer.writeNullable(value.confirmationMessage) { buffer.writeString(it) }
            buffer.writeBool(value.isHighlighted)
            buffer.writeNullable(value.iconName) { buffer.writeString(it) }
            buffer.writeNullable(value.displayDescription) { buffer.writeString(it) }
            buffer.writeEnum(value.state)
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
        
        other as ResourceCommand
        
        if (commandType != other.commandType) return false
        if (displayName != other.displayName) return false
        if (confirmationMessage != other.confirmationMessage) return false
        if (isHighlighted != other.isHighlighted) return false
        if (iconName != other.iconName) return false
        if (displayDescription != other.displayDescription) return false
        if (state != other.state) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + commandType.hashCode()
        __r = __r*31 + displayName.hashCode()
        __r = __r*31 + if (confirmationMessage != null) confirmationMessage.hashCode() else 0
        __r = __r*31 + isHighlighted.hashCode()
        __r = __r*31 + if (iconName != null) iconName.hashCode() else 0
        __r = __r*31 + if (displayDescription != null) displayDescription.hashCode() else 0
        __r = __r*31 + state.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceCommand (")
        printer.indent {
            print("commandType = "); commandType.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
            print("confirmationMessage = "); confirmationMessage.print(printer); println()
            print("isHighlighted = "); isHighlighted.print(printer); println()
            print("iconName = "); iconName.print(printer); println()
            print("displayDescription = "); displayDescription.print(printer); println()
            print("state = "); state.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:154]
 */
data class ResourceCommandRequest (
    val commandType: String,
    val resourceName: String,
    val resourceType: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceCommandRequest> {
        override val _type: KClass<ResourceCommandRequest> = ResourceCommandRequest::class
        override val id: RdId get() = RdId(-3460782994127365019)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommandRequest  {
            val commandType = buffer.readString()
            val resourceName = buffer.readString()
            val resourceType = buffer.readString()
            return ResourceCommandRequest(commandType, resourceName, resourceType)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommandRequest)  {
            buffer.writeString(value.commandType)
            buffer.writeString(value.resourceName)
            buffer.writeString(value.resourceType)
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
        
        other as ResourceCommandRequest
        
        if (commandType != other.commandType) return false
        if (resourceName != other.resourceName) return false
        if (resourceType != other.resourceType) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + commandType.hashCode()
        __r = __r*31 + resourceName.hashCode()
        __r = __r*31 + resourceType.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceCommandRequest (")
        printer.indent {
            print("commandType = "); commandType.print(printer); println()
            print("resourceName = "); resourceName.print(printer); println()
            print("resourceType = "); resourceType.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:160]
 */
data class ResourceCommandResponse (
    val kind: ResourceCommandResponseKind,
    val errorMessage: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceCommandResponse> {
        override val _type: KClass<ResourceCommandResponse> = ResourceCommandResponse::class
        override val id: RdId get() = RdId(3396191624361927979)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommandResponse  {
            val kind = buffer.readEnum<ResourceCommandResponseKind>()
            val errorMessage = buffer.readNullable { buffer.readString() }
            return ResourceCommandResponse(kind, errorMessage)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommandResponse)  {
            buffer.writeEnum(value.kind)
            buffer.writeNullable(value.errorMessage) { buffer.writeString(it) }
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
        
        other as ResourceCommandResponse
        
        if (kind != other.kind) return false
        if (errorMessage != other.errorMessage) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + kind.hashCode()
        __r = __r*31 + if (errorMessage != null) errorMessage.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceCommandResponse (")
        printer.indent {
            print("kind = "); kind.print(printer); println()
            print("errorMessage = "); errorMessage.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:161]
 */
enum class ResourceCommandResponseKind {
    Undefined, 
    Succeeded, 
    Failed, 
    Canceled;
    
    companion object : IMarshaller<ResourceCommandResponseKind> {
        val marshaller = FrameworkMarshallers.enum<ResourceCommandResponseKind>()
        
        
        override val _type: KClass<ResourceCommandResponseKind> = ResourceCommandResponseKind::class
        override val id: RdId get() = RdId(-8716242335550732449)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommandResponseKind {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommandResponseKind)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:141]
 */
enum class ResourceCommandState {
    Enabled, 
    Disabled, 
    Hidden;
    
    companion object : IMarshaller<ResourceCommandState> {
        val marshaller = FrameworkMarshallers.enum<ResourceCommandState>()
        
        
        override val _type: KClass<ResourceCommandState> = ResourceCommandState::class
        override val id: RdId get() = RdId(2722140349088377831)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommandState {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommandState)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:103]
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
 * #### Generated from [AspireSessionHostModel.kt:127]
 */
data class ResourceHealthReport (
    val status: ResourceHealthStatus,
    val key: String,
    val description: String,
    val exception: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceHealthReport> {
        override val _type: KClass<ResourceHealthReport> = ResourceHealthReport::class
        override val id: RdId get() = RdId(2840668839259467409)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceHealthReport  {
            val status = buffer.readEnum<ResourceHealthStatus>()
            val key = buffer.readString()
            val description = buffer.readString()
            val exception = buffer.readString()
            return ResourceHealthReport(status, key, description, exception)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceHealthReport)  {
            buffer.writeEnum(value.status)
            buffer.writeString(value.key)
            buffer.writeString(value.description)
            buffer.writeString(value.exception)
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
        
        other as ResourceHealthReport
        
        if (status != other.status) return false
        if (key != other.key) return false
        if (description != other.description) return false
        if (exception != other.exception) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + status.hashCode()
        __r = __r*31 + key.hashCode()
        __r = __r*31 + description.hashCode()
        __r = __r*31 + exception.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceHealthReport (")
        printer.indent {
            print("status = "); status.print(printer); println()
            print("key = "); key.print(printer); println()
            print("description = "); description.print(printer); println()
            print("exception = "); exception.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:121]
 */
enum class ResourceHealthStatus {
    Healthy, 
    Unhealthy, 
    Degraded;
    
    companion object : IMarshaller<ResourceHealthStatus> {
        val marshaller = FrameworkMarshallers.enum<ResourceHealthStatus>()
        
        
        override val _type: KClass<ResourceHealthStatus> = ResourceHealthStatus::class
        override val id: RdId get() = RdId(2840668839301507407)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceHealthStatus {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceHealthStatus)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * #### Generated from [AspireSessionHostModel.kt:148]
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
 * #### Generated from [AspireSessionHostModel.kt:58]
 */
data class ResourceModel (
    val name: String,
    val type: ResourceType,
    val displayName: String,
    val uid: String,
    val state: ResourceState?,
    val stateStyle: ResourceStateStyle?,
    val createdAt: Date?,
    val startedAt: Date?,
    val stoppedAt: Date?,
    val properties: Array<ResourceProperty>,
    val environment: Array<ResourceEnvironmentVariable>,
    val urls: Array<ResourceUrl>,
    val volumes: Array<ResourceVolume>,
    val healthStatus: ResourceHealthStatus?,
    val healthReports: Array<ResourceHealthReport>,
    val commands: Array<ResourceCommand>
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
            val createdAt = buffer.readNullable { buffer.readDateTime() }
            val startedAt = buffer.readNullable { buffer.readDateTime() }
            val stoppedAt = buffer.readNullable { buffer.readDateTime() }
            val properties = buffer.readArray {ResourceProperty.read(ctx, buffer)}
            val environment = buffer.readArray {ResourceEnvironmentVariable.read(ctx, buffer)}
            val urls = buffer.readArray {ResourceUrl.read(ctx, buffer)}
            val volumes = buffer.readArray {ResourceVolume.read(ctx, buffer)}
            val healthStatus = buffer.readNullable { buffer.readEnum<ResourceHealthStatus>() }
            val healthReports = buffer.readArray {ResourceHealthReport.read(ctx, buffer)}
            val commands = buffer.readArray {ResourceCommand.read(ctx, buffer)}
            return ResourceModel(name, type, displayName, uid, state, stateStyle, createdAt, startedAt, stoppedAt, properties, environment, urls, volumes, healthStatus, healthReports, commands)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceModel)  {
            buffer.writeString(value.name)
            buffer.writeEnum(value.type)
            buffer.writeString(value.displayName)
            buffer.writeString(value.uid)
            buffer.writeNullable(value.state) { buffer.writeEnum(it) }
            buffer.writeNullable(value.stateStyle) { buffer.writeEnum(it) }
            buffer.writeNullable(value.createdAt) { buffer.writeDateTime(it) }
            buffer.writeNullable(value.startedAt) { buffer.writeDateTime(it) }
            buffer.writeNullable(value.stoppedAt) { buffer.writeDateTime(it) }
            buffer.writeArray(value.properties) { ResourceProperty.write(ctx, buffer, it) }
            buffer.writeArray(value.environment) { ResourceEnvironmentVariable.write(ctx, buffer, it) }
            buffer.writeArray(value.urls) { ResourceUrl.write(ctx, buffer, it) }
            buffer.writeArray(value.volumes) { ResourceVolume.write(ctx, buffer, it) }
            buffer.writeNullable(value.healthStatus) { buffer.writeEnum(it) }
            buffer.writeArray(value.healthReports) { ResourceHealthReport.write(ctx, buffer, it) }
            buffer.writeArray(value.commands) { ResourceCommand.write(ctx, buffer, it) }
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
        if (startedAt != other.startedAt) return false
        if (stoppedAt != other.stoppedAt) return false
        if (!(properties contentDeepEquals other.properties)) return false
        if (!(environment contentDeepEquals other.environment)) return false
        if (!(urls contentDeepEquals other.urls)) return false
        if (!(volumes contentDeepEquals other.volumes)) return false
        if (healthStatus != other.healthStatus) return false
        if (!(healthReports contentDeepEquals other.healthReports)) return false
        if (!(commands contentDeepEquals other.commands)) return false
        
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
        __r = __r*31 + if (createdAt != null) createdAt.hashCode() else 0
        __r = __r*31 + if (startedAt != null) startedAt.hashCode() else 0
        __r = __r*31 + if (stoppedAt != null) stoppedAt.hashCode() else 0
        __r = __r*31 + properties.contentDeepHashCode()
        __r = __r*31 + environment.contentDeepHashCode()
        __r = __r*31 + urls.contentDeepHashCode()
        __r = __r*31 + volumes.contentDeepHashCode()
        __r = __r*31 + if (healthStatus != null) healthStatus.hashCode() else 0
        __r = __r*31 + healthReports.contentDeepHashCode()
        __r = __r*31 + commands.contentDeepHashCode()
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
            print("startedAt = "); startedAt.print(printer); println()
            print("stoppedAt = "); stoppedAt.print(printer); println()
            print("properties = "); properties.print(printer); println()
            print("environment = "); environment.print(printer); println()
            print("urls = "); urls.print(printer); println()
            print("volumes = "); volumes.print(printer); println()
            print("healthStatus = "); healthStatus.print(printer); println()
            print("healthReports = "); healthReports.print(printer); println()
            print("commands = "); commands.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:96]
 */
data class ResourceProperty (
    val name: String,
    val displayName: String?,
    val value: String?,
    val isSensitive: Boolean?
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
            val isSensitive = buffer.readNullable { buffer.readBool() }
            return ResourceProperty(name, displayName, value, isSensitive)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceProperty)  {
            buffer.writeString(value.name)
            buffer.writeNullable(value.displayName) { buffer.writeString(it) }
            buffer.writeNullable(value.value) { buffer.writeString(it) }
            buffer.writeNullable(value.isSensitive) { buffer.writeBool(it) }
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
        if (isSensitive != other.isSensitive) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + name.hashCode()
        __r = __r*31 + if (displayName != null) displayName.hashCode() else 0
        __r = __r*31 + if (value != null) value.hashCode() else 0
        __r = __r*31 + if (isSensitive != null) isSensitive.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceProperty (")
        printer.indent {
            print("name = "); name.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
            print("value = "); value.print(printer); println()
            print("isSensitive = "); isSensitive.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:68]
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
 * #### Generated from [AspireSessionHostModel.kt:77]
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
 * #### Generated from [AspireSessionHostModel.kt:60]
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
 * #### Generated from [AspireSessionHostModel.kt:108]
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
 * #### Generated from [AspireSessionHostModel.kt:114]
 */
data class ResourceVolume (
    val source: String,
    val target: String,
    val mountType: String,
    val isReadOnly: Boolean
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceVolume> {
        override val _type: KClass<ResourceVolume> = ResourceVolume::class
        override val id: RdId get() = RdId(-6198804010095377477)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceVolume  {
            val source = buffer.readString()
            val target = buffer.readString()
            val mountType = buffer.readString()
            val isReadOnly = buffer.readBool()
            return ResourceVolume(source, target, mountType, isReadOnly)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceVolume)  {
            buffer.writeString(value.source)
            buffer.writeString(value.target)
            buffer.writeString(value.mountType)
            buffer.writeBool(value.isReadOnly)
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
        
        other as ResourceVolume
        
        if (source != other.source) return false
        if (target != other.target) return false
        if (mountType != other.mountType) return false
        if (isReadOnly != other.isReadOnly) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + source.hashCode()
        __r = __r*31 + target.hashCode()
        __r = __r*31 + mountType.hashCode()
        __r = __r*31 + isReadOnly.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceVolume (")
        printer.indent {
            print("source = "); source.print(printer); println()
            print("target = "); target.print(printer); println()
            print("mountType = "); mountType.print(printer); println()
            print("isReadOnly = "); isReadOnly.print(printer); println()
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
    private val _logReceived: RdSignal<ResourceLog>,
    private val _executeCommand: RdCall<ResourceCommandRequest, ResourceCommandResponse>
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
            val _executeCommand = RdCall.read(ctx, buffer, ResourceCommandRequest, ResourceCommandResponse)
            return ResourceWrapper(_model, _isInitialized, _logReceived, _executeCommand).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceWrapper)  {
            value.rdid.write(buffer)
            RdOptionalProperty.write(ctx, buffer, value._model)
            RdOptionalProperty.write(ctx, buffer, value._isInitialized)
            RdSignal.write(ctx, buffer, value._logReceived)
            RdCall.write(ctx, buffer, value._executeCommand)
        }
        
        
    }
    //fields
    val model: IOptProperty<ResourceModel> get() = _model
    val isInitialized: IOptProperty<Boolean> get() = _isInitialized
    val logReceived: ISource<ResourceLog> get() = _logReceived
    val executeCommand: IRdCall<ResourceCommandRequest, ResourceCommandResponse> get() = _executeCommand
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
        bindableChildren.add("executeCommand" to _executeCommand)
    }
    
    //secondary constructor
    constructor(
    ) : this(
        RdOptionalProperty<ResourceModel>(ResourceModel),
        RdOptionalProperty<Boolean>(FrameworkMarshallers.Bool),
        RdSignal<ResourceLog>(ResourceLog),
        RdCall<ResourceCommandRequest, ResourceCommandResponse>(ResourceCommandRequest, ResourceCommandResponse)
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
            print("executeCommand = "); _executeCommand.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): ResourceWrapper   {
        return ResourceWrapper(
            _model.deepClonePolymorphic(),
            _isInitialized.deepClonePolymorphic(),
            _logReceived.deepClonePolymorphic(),
            _executeCommand.deepClonePolymorphic()
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
