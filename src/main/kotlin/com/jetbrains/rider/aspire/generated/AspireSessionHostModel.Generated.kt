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
            serializers.register(LazyCompanionMarshaller(RdId(3848038420960084968), classLoader, "com.jetbrains.rider.aspire.generated.CreateSessionRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(8608726607558258184), classLoader, "com.jetbrains.rider.aspire.generated.CreateSessionResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(945330335384668759), classLoader, "com.jetbrains.rider.aspire.generated.DeleteSessionRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-7588247750441437831), classLoader, "com.jetbrains.rider.aspire.generated.DeleteSessionResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-7695483574898099182), classLoader, "com.jetbrains.rider.aspire.generated.ResourceWrapper"))
            serializers.register(LazyCompanionMarshaller(RdId(-3770298982342277528), classLoader, "com.jetbrains.rider.aspire.generated.ResourceModel"))
            serializers.register(LazyCompanionMarshaller(RdId(1247681944195290678), classLoader, "com.jetbrains.rider.aspire.generated.ResourceProperty"))
            serializers.register(LazyCompanionMarshaller(RdId(-1423436662766610770), classLoader, "com.jetbrains.rider.aspire.generated.ResourceEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(552742225967993966), classLoader, "com.jetbrains.rider.aspire.generated.ResourceUrl"))
            serializers.register(LazyCompanionMarshaller(RdId(-6198804010095377477), classLoader, "com.jetbrains.rider.aspire.generated.ResourceVolume"))
            serializers.register(LazyCompanionMarshaller(RdId(2840668839301507407), classLoader, "com.jetbrains.rider.aspire.generated.ResourceHealthStatus"))
            serializers.register(LazyCompanionMarshaller(RdId(2840668839259467409), classLoader, "com.jetbrains.rider.aspire.generated.ResourceHealthReport"))
            serializers.register(LazyCompanionMarshaller(RdId(-7695483592723081526), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommand"))
            serializers.register(LazyCompanionMarshaller(RdId(3095035063992014361), classLoader, "com.jetbrains.rider.aspire.generated.ResourceRelationship"))
            serializers.register(LazyCompanionMarshaller(RdId(552742225967985219), classLoader, "com.jetbrains.rider.aspire.generated.ResourceLog"))
            serializers.register(LazyCompanionMarshaller(RdId(-3460782994127365019), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(3396191624361927979), classLoader, "com.jetbrains.rider.aspire.generated.ResourceCommandResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(8004637670271409586), classLoader, "com.jetbrains.rider.aspire.generated.AspireHostModelConfig"))
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
        
        
        const val serializationHash = 1011302472063179369L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostModel
    override val serializationHash: Long get() = AspireSessionHostModel.serializationHash
    
    //fields
    val aspireHosts: IMutableViewableMap<String, AspireHostModel> get() = _aspireHosts
    //methods
    //initializer
    init {
        bindableChildren.add("aspireHosts" to _aspireHosts)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdMap<String, AspireHostModel>(FrameworkMarshallers.String, AspireHostModel)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireSessionHostModel (")
        printer.indent {
            print("aspireHosts = "); _aspireHosts.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireSessionHostModel   {
        return AspireSessionHostModel(
            _aspireHosts.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireSessionHostModel get() = getOrCreateExtension(AspireSessionHostModel::class) { @Suppress("DEPRECATION") AspireSessionHostModel.create(lifetime, this) }



/**
 * #### Generated from [AspireSessionHostModel.kt:214]
 */
class AspireHostModel private constructor(
    val config: AspireHostModelConfig,
    private val _createSession: RdCall<CreateSessionRequest, CreateSessionResponse>,
    private val _deleteSession: RdCall<DeleteSessionRequest, DeleteSessionResponse>,
    private val _processStarted: RdSignal<ProcessStarted>,
    private val _processTerminated: RdSignal<ProcessTerminated>,
    private val _logReceived: RdSignal<LogReceived>,
    private val _resources: RdMap<String, ResourceWrapper>
) : RdBindableBase() {
    //companion
    
    companion object : IMarshaller<AspireHostModel> {
        override val _type: KClass<AspireHostModel> = AspireHostModel::class
        override val id: RdId get() = RdId(7370971417554020944)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostModel  {
            val _id = RdId.read(buffer)
            val config = AspireHostModelConfig.read(ctx, buffer)
            val _createSession = RdCall.read(ctx, buffer, CreateSessionRequest, CreateSessionResponse)
            val _deleteSession = RdCall.read(ctx, buffer, DeleteSessionRequest, DeleteSessionResponse)
            val _processStarted = RdSignal.read(ctx, buffer, ProcessStarted)
            val _processTerminated = RdSignal.read(ctx, buffer, ProcessTerminated)
            val _logReceived = RdSignal.read(ctx, buffer, LogReceived)
            val _resources = RdMap.read(ctx, buffer, FrameworkMarshallers.String, ResourceWrapper)
            return AspireHostModel(config, _createSession, _deleteSession, _processStarted, _processTerminated, _logReceived, _resources).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostModel)  {
            value.rdid.write(buffer)
            AspireHostModelConfig.write(ctx, buffer, value.config)
            RdCall.write(ctx, buffer, value._createSession)
            RdCall.write(ctx, buffer, value._deleteSession)
            RdSignal.write(ctx, buffer, value._processStarted)
            RdSignal.write(ctx, buffer, value._processTerminated)
            RdSignal.write(ctx, buffer, value._logReceived)
            RdMap.write(ctx, buffer, value._resources)
        }
        
        
    }
    //fields
    val createSession: IRdEndpoint<CreateSessionRequest, CreateSessionResponse> get() = _createSession
    val deleteSession: IRdEndpoint<DeleteSessionRequest, DeleteSessionResponse> get() = _deleteSession
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
    constructor(
        config: AspireHostModelConfig
    ) : this(
        config,
        RdCall<CreateSessionRequest, CreateSessionResponse>(CreateSessionRequest, CreateSessionResponse),
        RdCall<DeleteSessionRequest, DeleteSessionResponse>(DeleteSessionRequest, DeleteSessionResponse),
        RdSignal<ProcessStarted>(ProcessStarted),
        RdSignal<ProcessTerminated>(ProcessTerminated),
        RdSignal<LogReceived>(LogReceived),
        RdMap<String, ResourceWrapper>(FrameworkMarshallers.String, ResourceWrapper)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostModel (")
        printer.indent {
            print("config = "); config.print(printer); println()
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
    override fun deepClone(): AspireHostModel   {
        return AspireHostModel(
            config,
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
}


/**
 * @property id Unique identifier for the Aspire Host, created from the `DCP_INSTANCE_ID_PREFIX` environment variable
 * @property runConfigName Name of the started run configuration
 * @property aspireHostProjectPath Path of the Aspire Host .csproj file
 * @property resourceServiceEndpointUrl `ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` environment variable
 * @property resourceServiceApiKey `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable
 * @property otlpEndpointUrl `ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable
 * @property isDebuggingMode Is Aspire Host running with debugger attached
 * @property aspireHostProjectUrl URL of the Aspire Host dashboard
 * #### Generated from [AspireSessionHostModel.kt:194]
 */
data class AspireHostModelConfig (
    val id: String,
    val runConfigName: String?,
    val aspireHostProjectPath: String,
    val resourceServiceEndpointUrl: String?,
    val resourceServiceApiKey: String?,
    val otlpEndpointUrl: String?,
    val isDebuggingMode: Boolean,
    val aspireHostProjectUrl: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<AspireHostModelConfig> {
        override val _type: KClass<AspireHostModelConfig> = AspireHostModelConfig::class
        override val id: RdId get() = RdId(8004637670271409586)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostModelConfig  {
            val id = buffer.readString()
            val runConfigName = buffer.readNullable { buffer.readString() }
            val aspireHostProjectPath = buffer.readString()
            val resourceServiceEndpointUrl = buffer.readNullable { buffer.readString() }
            val resourceServiceApiKey = buffer.readNullable { buffer.readString() }
            val otlpEndpointUrl = buffer.readNullable { buffer.readString() }
            val isDebuggingMode = buffer.readBool()
            val aspireHostProjectUrl = buffer.readNullable { buffer.readString() }
            return AspireHostModelConfig(id, runConfigName, aspireHostProjectPath, resourceServiceEndpointUrl, resourceServiceApiKey, otlpEndpointUrl, isDebuggingMode, aspireHostProjectUrl)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostModelConfig)  {
            buffer.writeString(value.id)
            buffer.writeNullable(value.runConfigName) { buffer.writeString(it) }
            buffer.writeString(value.aspireHostProjectPath)
            buffer.writeNullable(value.resourceServiceEndpointUrl) { buffer.writeString(it) }
            buffer.writeNullable(value.resourceServiceApiKey) { buffer.writeString(it) }
            buffer.writeNullable(value.otlpEndpointUrl) { buffer.writeString(it) }
            buffer.writeBool(value.isDebuggingMode)
            buffer.writeNullable(value.aspireHostProjectUrl) { buffer.writeString(it) }
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
        
        other as AspireHostModelConfig
        
        if (id != other.id) return false
        if (runConfigName != other.runConfigName) return false
        if (aspireHostProjectPath != other.aspireHostProjectPath) return false
        if (resourceServiceEndpointUrl != other.resourceServiceEndpointUrl) return false
        if (resourceServiceApiKey != other.resourceServiceApiKey) return false
        if (otlpEndpointUrl != other.otlpEndpointUrl) return false
        if (isDebuggingMode != other.isDebuggingMode) return false
        if (aspireHostProjectUrl != other.aspireHostProjectUrl) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + if (runConfigName != null) runConfigName.hashCode() else 0
        __r = __r*31 + aspireHostProjectPath.hashCode()
        __r = __r*31 + if (resourceServiceEndpointUrl != null) resourceServiceEndpointUrl.hashCode() else 0
        __r = __r*31 + if (resourceServiceApiKey != null) resourceServiceApiKey.hashCode() else 0
        __r = __r*31 + if (otlpEndpointUrl != null) otlpEndpointUrl.hashCode() else 0
        __r = __r*31 + isDebuggingMode.hashCode()
        __r = __r*31 + if (aspireHostProjectUrl != null) aspireHostProjectUrl.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostModelConfig (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("runConfigName = "); runConfigName.print(printer); println()
            print("aspireHostProjectPath = "); aspireHostProjectPath.print(printer); println()
            print("resourceServiceEndpointUrl = "); resourceServiceEndpointUrl.print(printer); println()
            print("resourceServiceApiKey = "); resourceServiceApiKey.print(printer); println()
            print("otlpEndpointUrl = "); otlpEndpointUrl.print(printer); println()
            print("isDebuggingMode = "); isDebuggingMode.print(printer); println()
            print("aspireHostProjectUrl = "); aspireHostProjectUrl.print(printer); println()
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
data class CreateSessionRequest (
    val projectPath: String,
    val debug: Boolean,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: Array<String>?,
    val envs: Array<SessionEnvironmentVariable>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<CreateSessionRequest> {
        override val _type: KClass<CreateSessionRequest> = CreateSessionRequest::class
        override val id: RdId get() = RdId(3848038420960084968)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreateSessionRequest  {
            val projectPath = buffer.readString()
            val debug = buffer.readBool()
            val launchProfile = buffer.readNullable { buffer.readString() }
            val disableLaunchProfile = buffer.readBool()
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val envs = buffer.readNullable { buffer.readArray {SessionEnvironmentVariable.read(ctx, buffer)} }
            return CreateSessionRequest(projectPath, debug, launchProfile, disableLaunchProfile, args, envs)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreateSessionRequest)  {
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
        
        other as CreateSessionRequest
        
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
        printer.println("CreateSessionRequest (")
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


/**
 * #### Generated from [AspireSessionHostModel.kt:47]
 */
data class CreateSessionResponse (
    val sessionId: String?,
    val error: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<CreateSessionResponse> {
        override val _type: KClass<CreateSessionResponse> = CreateSessionResponse::class
        override val id: RdId get() = RdId(8608726607558258184)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreateSessionResponse  {
            val sessionId = buffer.readNullable { buffer.readString() }
            val error = buffer.readNullable { buffer.readString() }
            return CreateSessionResponse(sessionId, error)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreateSessionResponse)  {
            buffer.writeNullable(value.sessionId) { buffer.writeString(it) }
            buffer.writeNullable(value.error) { buffer.writeString(it) }
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
        
        other as CreateSessionResponse
        
        if (sessionId != other.sessionId) return false
        if (error != other.error) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (sessionId != null) sessionId.hashCode() else 0
        __r = __r*31 + if (error != null) error.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CreateSessionResponse (")
        printer.indent {
            print("sessionId = "); sessionId.print(printer); println()
            print("error = "); error.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:52]
 */
data class DeleteSessionRequest (
    val sessionId: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<DeleteSessionRequest> {
        override val _type: KClass<DeleteSessionRequest> = DeleteSessionRequest::class
        override val id: RdId get() = RdId(945330335384668759)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): DeleteSessionRequest  {
            val sessionId = buffer.readString()
            return DeleteSessionRequest(sessionId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: DeleteSessionRequest)  {
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
        
        other as DeleteSessionRequest
        
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
        printer.println("DeleteSessionRequest (")
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
 * @property sessionId The field will be null if the session cannot be found
 * #### Generated from [AspireSessionHostModel.kt:56]
 */
data class DeleteSessionResponse (
    val sessionId: String?,
    val error: String?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<DeleteSessionResponse> {
        override val _type: KClass<DeleteSessionResponse> = DeleteSessionResponse::class
        override val id: RdId get() = RdId(-7588247750441437831)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): DeleteSessionResponse  {
            val sessionId = buffer.readNullable { buffer.readString() }
            val error = buffer.readNullable { buffer.readString() }
            return DeleteSessionResponse(sessionId, error)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: DeleteSessionResponse)  {
            buffer.writeNullable(value.sessionId) { buffer.writeString(it) }
            buffer.writeNullable(value.error) { buffer.writeString(it) }
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
        
        other as DeleteSessionResponse
        
        if (sessionId != other.sessionId) return false
        if (error != other.error) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (sessionId != null) sessionId.hashCode() else 0
        __r = __r*31 + if (error != null) error.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("DeleteSessionResponse (")
        printer.indent {
            print("sessionId = "); sessionId.print(printer); println()
            print("error = "); error.print(printer); println()
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
 * #### Generated from [AspireSessionHostModel.kt:153]
 */
data class ResourceCommand (
    val name: String,
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
            val name = buffer.readString()
            val displayName = buffer.readString()
            val confirmationMessage = buffer.readNullable { buffer.readString() }
            val isHighlighted = buffer.readBool()
            val iconName = buffer.readNullable { buffer.readString() }
            val displayDescription = buffer.readNullable { buffer.readString() }
            val state = buffer.readEnum<ResourceCommandState>()
            return ResourceCommand(name, displayName, confirmationMessage, isHighlighted, iconName, displayDescription, state)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommand)  {
            buffer.writeString(value.name)
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
        
        if (name != other.name) return false
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
        __r = __r*31 + name.hashCode()
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
            print("name = "); name.print(printer); println()
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
 * #### Generated from [AspireSessionHostModel.kt:178]
 */
data class ResourceCommandRequest (
    val commandName: String,
    val resourceName: String,
    val resourceType: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceCommandRequest> {
        override val _type: KClass<ResourceCommandRequest> = ResourceCommandRequest::class
        override val id: RdId get() = RdId(-3460782994127365019)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceCommandRequest  {
            val commandName = buffer.readString()
            val resourceName = buffer.readString()
            val resourceType = buffer.readString()
            return ResourceCommandRequest(commandName, resourceName, resourceType)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceCommandRequest)  {
            buffer.writeString(value.commandName)
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
        
        if (commandName != other.commandName) return false
        if (resourceName != other.resourceName) return false
        if (resourceType != other.resourceType) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + commandName.hashCode()
        __r = __r*31 + resourceName.hashCode()
        __r = __r*31 + resourceType.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceCommandRequest (")
        printer.indent {
            print("commandName = "); commandName.print(printer); println()
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
 * #### Generated from [AspireSessionHostModel.kt:184]
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
 * #### Generated from [AspireSessionHostModel.kt:185]
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
 * #### Generated from [AspireSessionHostModel.kt:160]
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
 * #### Generated from [AspireSessionHostModel.kt:119]
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
 * #### Generated from [AspireSessionHostModel.kt:146]
 */
data class ResourceHealthReport (
    val status: ResourceHealthStatus?,
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
            val status = buffer.readNullable { buffer.readEnum<ResourceHealthStatus>() }
            val key = buffer.readString()
            val description = buffer.readString()
            val exception = buffer.readString()
            return ResourceHealthReport(status, key, description, exception)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceHealthReport)  {
            buffer.writeNullable(value.status) { buffer.writeEnum(it) }
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
        __r = __r*31 + if (status != null) status.hashCode() else 0
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
 * #### Generated from [AspireSessionHostModel.kt:140]
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
 * #### Generated from [AspireSessionHostModel.kt:172]
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
 * #### Generated from [AspireSessionHostModel.kt:69]
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
    val healthReports: Array<ResourceHealthReport>,
    val commands: Array<ResourceCommand>,
    val relationships: Array<ResourceRelationship>,
    val isHidden: Boolean
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
            val healthReports = buffer.readArray {ResourceHealthReport.read(ctx, buffer)}
            val commands = buffer.readArray {ResourceCommand.read(ctx, buffer)}
            val relationships = buffer.readArray {ResourceRelationship.read(ctx, buffer)}
            val isHidden = buffer.readBool()
            return ResourceModel(name, type, displayName, uid, state, stateStyle, createdAt, startedAt, stoppedAt, properties, environment, urls, volumes, healthReports, commands, relationships, isHidden)
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
            buffer.writeArray(value.healthReports) { ResourceHealthReport.write(ctx, buffer, it) }
            buffer.writeArray(value.commands) { ResourceCommand.write(ctx, buffer, it) }
            buffer.writeArray(value.relationships) { ResourceRelationship.write(ctx, buffer, it) }
            buffer.writeBool(value.isHidden)
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
        if (!(healthReports contentDeepEquals other.healthReports)) return false
        if (!(commands contentDeepEquals other.commands)) return false
        if (!(relationships contentDeepEquals other.relationships)) return false
        if (isHidden != other.isHidden) return false
        
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
        __r = __r*31 + healthReports.contentDeepHashCode()
        __r = __r*31 + commands.contentDeepHashCode()
        __r = __r*31 + relationships.contentDeepHashCode()
        __r = __r*31 + isHidden.hashCode()
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
            print("healthReports = "); healthReports.print(printer); println()
            print("commands = "); commands.print(printer); println()
            print("relationships = "); relationships.print(printer); println()
            print("isHidden = "); isHidden.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:112]
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
 * #### Generated from [AspireSessionHostModel.kt:167]
 */
data class ResourceRelationship (
    val resourceName: String,
    val type: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceRelationship> {
        override val _type: KClass<ResourceRelationship> = ResourceRelationship::class
        override val id: RdId get() = RdId(3095035063992014361)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceRelationship  {
            val resourceName = buffer.readString()
            val type = buffer.readString()
            return ResourceRelationship(resourceName, type)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceRelationship)  {
            buffer.writeString(value.resourceName)
            buffer.writeString(value.type)
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
        
        other as ResourceRelationship
        
        if (resourceName != other.resourceName) return false
        if (type != other.type) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + resourceName.hashCode()
        __r = __r*31 + type.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceRelationship (")
        printer.indent {
            print("resourceName = "); resourceName.print(printer); println()
            print("type = "); type.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:79]
 */
enum class ResourceState {
    Starting, 
    Running, 
    FailedToStart, 
    RuntimeUnhealthy, 
    Stopping, 
    Exited, 
    Finished, 
    Waiting, 
    NotStarted, 
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
 * #### Generated from [AspireSessionHostModel.kt:92]
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
 * #### Generated from [AspireSessionHostModel.kt:71]
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
 * #### Generated from [AspireSessionHostModel.kt:124]
 */
data class ResourceUrl (
    val endpointName: String?,
    val fullUrl: String,
    val isInternal: Boolean,
    val isInactive: Boolean,
    val sortOrder: Int,
    val displayName: String
) : IPrintable {
    //companion
    
    companion object : IMarshaller<ResourceUrl> {
        override val _type: KClass<ResourceUrl> = ResourceUrl::class
        override val id: RdId get() = RdId(552742225967993966)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ResourceUrl  {
            val endpointName = buffer.readNullable { buffer.readString() }
            val fullUrl = buffer.readString()
            val isInternal = buffer.readBool()
            val isInactive = buffer.readBool()
            val sortOrder = buffer.readInt()
            val displayName = buffer.readString()
            return ResourceUrl(endpointName, fullUrl, isInternal, isInactive, sortOrder, displayName)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ResourceUrl)  {
            buffer.writeNullable(value.endpointName) { buffer.writeString(it) }
            buffer.writeString(value.fullUrl)
            buffer.writeBool(value.isInternal)
            buffer.writeBool(value.isInactive)
            buffer.writeInt(value.sortOrder)
            buffer.writeString(value.displayName)
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
        
        if (endpointName != other.endpointName) return false
        if (fullUrl != other.fullUrl) return false
        if (isInternal != other.isInternal) return false
        if (isInactive != other.isInactive) return false
        if (sortOrder != other.sortOrder) return false
        if (displayName != other.displayName) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + if (endpointName != null) endpointName.hashCode() else 0
        __r = __r*31 + fullUrl.hashCode()
        __r = __r*31 + isInternal.hashCode()
        __r = __r*31 + isInactive.hashCode()
        __r = __r*31 + sortOrder.hashCode()
        __r = __r*31 + displayName.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ResourceUrl (")
        printer.indent {
            print("endpointName = "); endpointName.print(printer); println()
            print("fullUrl = "); fullUrl.print(printer); println()
            print("isInternal = "); isInternal.print(printer); println()
            print("isInactive = "); isInactive.print(printer); println()
            print("sortOrder = "); sortOrder.print(printer); println()
            print("displayName = "); displayName.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireSessionHostModel.kt:133]
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
 * #### Generated from [AspireSessionHostModel.kt:62]
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
