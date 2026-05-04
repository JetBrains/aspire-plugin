@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.jetbrains.aspire.generated

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
 * #### Generated from [AspireWorkerModel.kt:15]
 */
class AspireWorkerModel private constructor(
    private val _aspireHosts: RdMap<String, AspireHostModel>,
    private val _createSession: RdCall<CreateSessionRequest, CreateSessionResponse>,
    private val _deleteSession: RdCall<DeleteSessionRequest, DeleteSessionResponse>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-8012683471335252475), classLoader, "com.jetbrains.aspire.generated.ProcessStarted"))
            serializers.register(LazyCompanionMarshaller(RdId(-4984966637681634785), classLoader, "com.jetbrains.aspire.generated.ProcessTerminated"))
            serializers.register(LazyCompanionMarshaller(RdId(548077805281958706), classLoader, "com.jetbrains.aspire.generated.LogReceived"))
            serializers.register(LazyCompanionMarshaller(RdId(7699071206331680373), classLoader, "com.jetbrains.aspire.generated.MessageReceived"))
            serializers.register(LazyCompanionMarshaller(RdId(-5369615389742325332), classLoader, "com.jetbrains.aspire.generated.SessionEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(7408902590388970211), classLoader, "com.jetbrains.aspire.generated.CreateProjectSessionRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(3754635917469923492), classLoader, "com.jetbrains.aspire.generated.CreatePythonSessionRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(8608726607558258184), classLoader, "com.jetbrains.aspire.generated.CreateSessionResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(945330335384668759), classLoader, "com.jetbrains.aspire.generated.DeleteSessionRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-7588247750441437831), classLoader, "com.jetbrains.aspire.generated.DeleteSessionResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(8004637670271409586), classLoader, "com.jetbrains.aspire.generated.AspireHostModelConfig"))
            serializers.register(LazyCompanionMarshaller(RdId(7370971417554020944), classLoader, "com.jetbrains.aspire.generated.AspireHostModel"))
            serializers.register(LazyCompanionMarshaller(RdId(564443201287490), classLoader, "com.jetbrains.aspire.generated.ErrorCode"))
            serializers.register(LazyCompanionMarshaller(RdId(-1438774601599785104), classLoader, "com.jetbrains.aspire.generated.MessageLevel"))
            serializers.register(LazyCompanionMarshaller(RdId(7213467228002387667), classLoader, "com.jetbrains.aspire.generated.CreateSessionRequest_Unknown"))
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): AspireWorkerModel  {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.aspireWorkerModel or revise the extension scope instead", ReplaceWith("protocol.aspireWorkerModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): AspireWorkerModel  {
            AspireWorkerRoot.register(protocol.serializers)
            
            return AspireWorkerModel()
        }
        
        
        const val serializationHash = -5723524623006190095L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireWorkerModel
    override val serializationHash: Long get() = AspireWorkerModel.serializationHash
    
    //fields
    val aspireHosts: IMutableViewableMap<String, AspireHostModel> get() = _aspireHosts
    
    /**
     * Used to create a new run session for a particular Executable
     */
    val createSession: IRdEndpoint<CreateSessionRequest, CreateSessionResponse> get() = _createSession
    
    /**
     * Used to stop an in-progress run session
     */
    val deleteSession: IRdEndpoint<DeleteSessionRequest, DeleteSessionResponse> get() = _deleteSession
    //methods
    //initializer
    init {
        bindableChildren.add("aspireHosts" to _aspireHosts)
        bindableChildren.add("createSession" to _createSession)
        bindableChildren.add("deleteSession" to _deleteSession)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdMap<String, AspireHostModel>(FrameworkMarshallers.String, AspireHostModel),
        RdCall<CreateSessionRequest, CreateSessionResponse>(AbstractPolymorphic(CreateSessionRequest), CreateSessionResponse),
        RdCall<DeleteSessionRequest, DeleteSessionResponse>(DeleteSessionRequest, DeleteSessionResponse)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireWorkerModel (")
        printer.indent {
            print("aspireHosts = "); _aspireHosts.print(printer); println()
            print("createSession = "); _createSession.print(printer); println()
            print("deleteSession = "); _deleteSession.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireWorkerModel   {
        return AspireWorkerModel(
            _aspireHosts.deepClonePolymorphic(),
            _createSession.deepClonePolymorphic(),
            _deleteSession.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireWorkerModel get() = getOrCreateExtension(AspireWorkerModel::class) { @Suppress("DEPRECATION") AspireWorkerModel.create(lifetime, this) }



/**
 * #### Generated from [AspireWorkerModel.kt:110]
 */
class AspireHostModel private constructor(
    val config: AspireHostModelConfig,
    private val _processStarted: RdSignal<ProcessStarted>,
    private val _processTerminated: RdSignal<ProcessTerminated>,
    private val _logReceived: RdSignal<LogReceived>,
    private val _messageReceived: RdSignal<MessageReceived>
) : RdBindableBase() {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        rdid.write(buffer)
        AspireHostModelConfig.write(ctx, buffer, config)
        RdSignal.write(ctx, buffer, _processStarted)
        RdSignal.write(ctx, buffer, _processTerminated)
        RdSignal.write(ctx, buffer, _logReceived)
        RdSignal.write(ctx, buffer, _messageReceived)
    }
    //companion
    
    companion object : IMarshaller<AspireHostModel> {
        override val _type: KClass<AspireHostModel> = AspireHostModel::class
        override val id: RdId get() = RdId(7370971417554020944)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostModel  {
            val _id = RdId.read(buffer)
            val config = AspireHostModelConfig.read(ctx, buffer)
            val _processStarted = RdSignal.read(ctx, buffer, ProcessStarted)
            val _processTerminated = RdSignal.read(ctx, buffer, ProcessTerminated)
            val _logReceived = RdSignal.read(ctx, buffer, LogReceived)
            val _messageReceived = RdSignal.read(ctx, buffer, MessageReceived)
            return AspireHostModel(config, _processStarted, _processTerminated, _logReceived, _messageReceived).withId(_id)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostModel)  {
            value.write(ctx, buffer)
        }
        
        
    }
    //fields
    
    /**
     * The notification is emitted when the run is started or the IDE restarts the service.
     */
    val processStarted: ISignal<ProcessStarted> get() = _processStarted
    
    /**
     * The notification is emitted when the session is terminated (the program ends, or is terminated by the developer)
     */
    val processTerminated: ISignal<ProcessTerminated> get() = _processTerminated
    
    /**
     * The notification is emitted when the service program writes something to standard output stream (stdout) or standard error (stderr)
     */
    val logReceived: ISignal<LogReceived> get() = _logReceived
    
    /**
     * The notification is emitted when the IDE needs to notify the client (and the Aspire developer) about asynchronous events related to a debug session
     */
    val messageReceived: ISignal<MessageReceived> get() = _messageReceived
    //methods
    //initializer
    init {
        bindableChildren.add("processStarted" to _processStarted)
        bindableChildren.add("processTerminated" to _processTerminated)
        bindableChildren.add("logReceived" to _logReceived)
        bindableChildren.add("messageReceived" to _messageReceived)
    }
    
    //secondary constructor
    constructor(
        config: AspireHostModelConfig
    ) : this(
        config,
        RdSignal<ProcessStarted>(ProcessStarted),
        RdSignal<ProcessTerminated>(ProcessTerminated),
        RdSignal<LogReceived>(LogReceived),
        RdSignal<MessageReceived>(MessageReceived)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostModel (")
        printer.indent {
            print("config = "); config.print(printer); println()
            print("processStarted = "); _processStarted.print(printer); println()
            print("processTerminated = "); _processTerminated.print(printer); println()
            print("logReceived = "); _logReceived.print(printer); println()
            print("messageReceived = "); _messageReceived.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireHostModel   {
        return AspireHostModel(
            config,
            _processStarted.deepClonePolymorphic(),
            _processTerminated.deepClonePolymorphic(),
            _logReceived.deepClonePolymorphic(),
            _messageReceived.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
}


/**
 * @property id Unique identifier for the Aspire Host, created from the `DCP_INSTANCE_ID_PREFIX` environment variable
 * @property aspireHostProjectPath Path of the Aspire Host .csproj file
 * @property resourceServiceEndpointUrl `ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` environment variable
 * @property resourceServiceApiKey `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable
 * @property otlpEndpointUrl `ASPIRE_DASHBOARD_OTLP_ENDPOINT_URL` environment variable
 * @property aspireHostProjectUrl URL of the Aspire Host dashboard
 * #### Generated from [AspireWorkerModel.kt:94]
 */
data class AspireHostModelConfig (
    val id: String,
    val aspireHostProjectPath: String,
    val resourceServiceEndpointUrl: String?,
    val resourceServiceApiKey: String?,
    val otlpEndpointUrl: String?,
    val aspireHostProjectUrl: String?
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(id)
        buffer.writeString(aspireHostProjectPath)
        buffer.writeNullable(resourceServiceEndpointUrl) { buffer.writeString(it) }
        buffer.writeNullable(resourceServiceApiKey) { buffer.writeString(it) }
        buffer.writeNullable(otlpEndpointUrl) { buffer.writeString(it) }
        buffer.writeNullable(aspireHostProjectUrl) { buffer.writeString(it) }
    }
    //companion
    
    companion object : IMarshaller<AspireHostModelConfig> {
        override val _type: KClass<AspireHostModelConfig> = AspireHostModelConfig::class
        override val id: RdId get() = RdId(8004637670271409586)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostModelConfig  {
            val id = buffer.readString()
            val aspireHostProjectPath = buffer.readString()
            val resourceServiceEndpointUrl = buffer.readNullable { buffer.readString() }
            val resourceServiceApiKey = buffer.readNullable { buffer.readString() }
            val otlpEndpointUrl = buffer.readNullable { buffer.readString() }
            val aspireHostProjectUrl = buffer.readNullable { buffer.readString() }
            return AspireHostModelConfig(id, aspireHostProjectPath, resourceServiceEndpointUrl, resourceServiceApiKey, otlpEndpointUrl, aspireHostProjectUrl)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostModelConfig)  {
            value.write(ctx, buffer)
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
        if (aspireHostProjectPath != other.aspireHostProjectPath) return false
        if (resourceServiceEndpointUrl != other.resourceServiceEndpointUrl) return false
        if (resourceServiceApiKey != other.resourceServiceApiKey) return false
        if (otlpEndpointUrl != other.otlpEndpointUrl) return false
        if (aspireHostProjectUrl != other.aspireHostProjectUrl) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + aspireHostProjectPath.hashCode()
        __r = __r*31 + if (resourceServiceEndpointUrl != null) resourceServiceEndpointUrl.hashCode() else 0
        __r = __r*31 + if (resourceServiceApiKey != null) resourceServiceApiKey.hashCode() else 0
        __r = __r*31 + if (otlpEndpointUrl != null) otlpEndpointUrl.hashCode() else 0
        __r = __r*31 + if (aspireHostProjectUrl != null) aspireHostProjectUrl.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireHostModelConfig (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("aspireHostProjectPath = "); aspireHostProjectPath.print(printer); println()
            print("resourceServiceEndpointUrl = "); resourceServiceEndpointUrl.print(printer); println()
            print("resourceServiceApiKey = "); resourceServiceApiKey.print(printer); println()
            print("otlpEndpointUrl = "); otlpEndpointUrl.print(printer); println()
            print("aspireHostProjectUrl = "); aspireHostProjectUrl.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireWorkerModel.kt:66]
 */
class CreateProjectSessionRequest (
    val projectPath: String,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    dcpInstancePrefix: String,
    debug: Boolean,
    args: Array<String>?,
    envs: Array<SessionEnvironmentVariable>?
) : CreateSessionRequest (
    dcpInstancePrefix,
    debug,
    args,
    envs
) {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(dcpInstancePrefix)
        buffer.writeBool(debug)
        buffer.writeNullable(args) { buffer.writeArray(it) { buffer.writeString(it) } }
        buffer.writeNullable(envs) { buffer.writeArray(it) { SessionEnvironmentVariable.write(ctx, buffer, it) } }
        buffer.writeString(projectPath)
        buffer.writeNullable(launchProfile) { buffer.writeString(it) }
        buffer.writeBool(disableLaunchProfile)
    }
    //companion
    
    companion object : IMarshaller<CreateProjectSessionRequest> {
        override val _type: KClass<CreateProjectSessionRequest> = CreateProjectSessionRequest::class
        override val id: RdId get() = RdId(7408902590388970211)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreateProjectSessionRequest  {
            val dcpInstancePrefix = buffer.readString()
            val debug = buffer.readBool()
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val envs = buffer.readNullable { buffer.readArray {SessionEnvironmentVariable.read(ctx, buffer)} }
            val projectPath = buffer.readString()
            val launchProfile = buffer.readNullable { buffer.readString() }
            val disableLaunchProfile = buffer.readBool()
            return CreateProjectSessionRequest(projectPath, launchProfile, disableLaunchProfile, dcpInstancePrefix, debug, args, envs)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreateProjectSessionRequest)  {
            value.write(ctx, buffer)
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
        
        other as CreateProjectSessionRequest
        
        if (projectPath != other.projectPath) return false
        if (launchProfile != other.launchProfile) return false
        if (disableLaunchProfile != other.disableLaunchProfile) return false
        if (dcpInstancePrefix != other.dcpInstancePrefix) return false
        if (debug != other.debug) return false
        if (args != other.args) return false
        if (envs != other.envs) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + projectPath.hashCode()
        __r = __r*31 + if (launchProfile != null) launchProfile.hashCode() else 0
        __r = __r*31 + disableLaunchProfile.hashCode()
        __r = __r*31 + dcpInstancePrefix.hashCode()
        __r = __r*31 + debug.hashCode()
        __r = __r*31 + if (args != null) args.contentDeepHashCode() else 0
        __r = __r*31 + if (envs != null) envs.contentDeepHashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CreateProjectSessionRequest (")
        printer.indent {
            print("projectPath = "); projectPath.print(printer); println()
            print("launchProfile = "); launchProfile.print(printer); println()
            print("disableLaunchProfile = "); disableLaunchProfile.print(printer); println()
            print("dcpInstancePrefix = "); dcpInstancePrefix.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("args = "); args.print(printer); println()
            print("envs = "); envs.print(printer); println()
        }
        printer.print(")")
    }
    
    override fun toString() = PrettyPrinter().singleLine().also { print(it) }.toString()
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireWorkerModel.kt:72]
 */
class CreatePythonSessionRequest (
    val programPath: String,
    val interpreterPath: String?,
    val module: String?,
    dcpInstancePrefix: String,
    debug: Boolean,
    args: Array<String>?,
    envs: Array<SessionEnvironmentVariable>?
) : CreateSessionRequest (
    dcpInstancePrefix,
    debug,
    args,
    envs
) {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(dcpInstancePrefix)
        buffer.writeBool(debug)
        buffer.writeNullable(args) { buffer.writeArray(it) { buffer.writeString(it) } }
        buffer.writeNullable(envs) { buffer.writeArray(it) { SessionEnvironmentVariable.write(ctx, buffer, it) } }
        buffer.writeString(programPath)
        buffer.writeNullable(interpreterPath) { buffer.writeString(it) }
        buffer.writeNullable(module) { buffer.writeString(it) }
    }
    //companion
    
    companion object : IMarshaller<CreatePythonSessionRequest> {
        override val _type: KClass<CreatePythonSessionRequest> = CreatePythonSessionRequest::class
        override val id: RdId get() = RdId(3754635917469923492)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreatePythonSessionRequest  {
            val dcpInstancePrefix = buffer.readString()
            val debug = buffer.readBool()
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val envs = buffer.readNullable { buffer.readArray {SessionEnvironmentVariable.read(ctx, buffer)} }
            val programPath = buffer.readString()
            val interpreterPath = buffer.readNullable { buffer.readString() }
            val module = buffer.readNullable { buffer.readString() }
            return CreatePythonSessionRequest(programPath, interpreterPath, module, dcpInstancePrefix, debug, args, envs)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreatePythonSessionRequest)  {
            value.write(ctx, buffer)
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
        
        other as CreatePythonSessionRequest
        
        if (programPath != other.programPath) return false
        if (interpreterPath != other.interpreterPath) return false
        if (module != other.module) return false
        if (dcpInstancePrefix != other.dcpInstancePrefix) return false
        if (debug != other.debug) return false
        if (args != other.args) return false
        if (envs != other.envs) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + programPath.hashCode()
        __r = __r*31 + if (interpreterPath != null) interpreterPath.hashCode() else 0
        __r = __r*31 + if (module != null) module.hashCode() else 0
        __r = __r*31 + dcpInstancePrefix.hashCode()
        __r = __r*31 + debug.hashCode()
        __r = __r*31 + if (args != null) args.contentDeepHashCode() else 0
        __r = __r*31 + if (envs != null) envs.contentDeepHashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CreatePythonSessionRequest (")
        printer.indent {
            print("programPath = "); programPath.print(printer); println()
            print("interpreterPath = "); interpreterPath.print(printer); println()
            print("module = "); module.print(printer); println()
            print("dcpInstancePrefix = "); dcpInstancePrefix.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("args = "); args.print(printer); println()
            print("envs = "); envs.print(printer); println()
        }
        printer.print(")")
    }
    
    override fun toString() = PrettyPrinter().singleLine().also { print(it) }.toString()
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireWorkerModel.kt:59]
 */
abstract class CreateSessionRequest (
    val dcpInstancePrefix: String,
    val debug: Boolean,
    val args: Array<String>?,
    val envs: Array<SessionEnvironmentVariable>?
) : IPrintable {
    //companion
    
    companion object : IAbstractDeclaration<CreateSessionRequest> {
        override fun readUnknownInstance(ctx: SerializationCtx, buffer: AbstractBuffer, unknownId: RdId, size: Int): CreateSessionRequest  {
            val objectStartPosition = buffer.position
            val dcpInstancePrefix = buffer.readString()
            val debug = buffer.readBool()
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            val envs = buffer.readNullable { buffer.readArray {SessionEnvironmentVariable.read(ctx, buffer)} }
            val unknownBytes = ByteArray(objectStartPosition + size - buffer.position)
            buffer.readByteArrayRaw(unknownBytes)
            return CreateSessionRequest_Unknown(dcpInstancePrefix, debug, args, envs, unknownId, unknownBytes)
        }
        
        
    }
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    //hash code trait
    //pretty print
    //deepClone
    //contexts
    //threading
}


class CreateSessionRequest_Unknown (
    dcpInstancePrefix: String,
    debug: Boolean,
    args: Array<String>?,
    envs: Array<SessionEnvironmentVariable>?,
    override val unknownId: RdId,
    val unknownBytes: ByteArray
) : CreateSessionRequest (
    dcpInstancePrefix,
    debug,
    args,
    envs
), IUnknownInstance {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(dcpInstancePrefix)
        buffer.writeBool(debug)
        buffer.writeNullable(args) { buffer.writeArray(it) { buffer.writeString(it) } }
        buffer.writeNullable(envs) { buffer.writeArray(it) { SessionEnvironmentVariable.write(ctx, buffer, it) } }
        buffer.writeByteArrayRaw(unknownBytes)
    }
    //companion
    
    companion object : IMarshaller<CreateSessionRequest_Unknown> {
        override val _type: KClass<CreateSessionRequest_Unknown> = CreateSessionRequest_Unknown::class
        override val id: RdId get() = RdId(7213467228002387667)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreateSessionRequest_Unknown  {
            throw NotImplementedError("Unknown instances should not be read via serializer")
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreateSessionRequest_Unknown)  {
            value.write(ctx, buffer)
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
        
        other as CreateSessionRequest_Unknown
        
        if (dcpInstancePrefix != other.dcpInstancePrefix) return false
        if (debug != other.debug) return false
        if (args != other.args) return false
        if (envs != other.envs) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + dcpInstancePrefix.hashCode()
        __r = __r*31 + debug.hashCode()
        __r = __r*31 + if (args != null) args.contentDeepHashCode() else 0
        __r = __r*31 + if (envs != null) envs.contentDeepHashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("CreateSessionRequest_Unknown (")
        printer.indent {
            print("dcpInstancePrefix = "); dcpInstancePrefix.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("args = "); args.print(printer); println()
            print("envs = "); envs.print(printer); println()
        }
        printer.print(")")
    }
    
    override fun toString() = PrettyPrinter().singleLine().also { print(it) }.toString()
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspireWorkerModel.kt:78]
 */
data class CreateSessionResponse (
    val sessionId: String?,
    val error: ErrorCode?
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeNullable(sessionId) { buffer.writeString(it) }
        buffer.writeNullable(error) { buffer.writeEnum(it) }
    }
    //companion
    
    companion object : IMarshaller<CreateSessionResponse> {
        override val _type: KClass<CreateSessionResponse> = CreateSessionResponse::class
        override val id: RdId get() = RdId(8608726607558258184)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): CreateSessionResponse  {
            val sessionId = buffer.readNullable { buffer.readString() }
            val error = buffer.readNullable { buffer.readEnum<ErrorCode>() }
            return CreateSessionResponse(sessionId, error)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: CreateSessionResponse)  {
            value.write(ctx, buffer)
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
 * #### Generated from [AspireWorkerModel.kt:83]
 */
data class DeleteSessionRequest (
    val dcpInstancePrefix: String,
    val sessionId: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(dcpInstancePrefix)
        buffer.writeString(sessionId)
    }
    //companion
    
    companion object : IMarshaller<DeleteSessionRequest> {
        override val _type: KClass<DeleteSessionRequest> = DeleteSessionRequest::class
        override val id: RdId get() = RdId(945330335384668759)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): DeleteSessionRequest  {
            val dcpInstancePrefix = buffer.readString()
            val sessionId = buffer.readString()
            return DeleteSessionRequest(dcpInstancePrefix, sessionId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: DeleteSessionRequest)  {
            value.write(ctx, buffer)
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
        
        if (dcpInstancePrefix != other.dcpInstancePrefix) return false
        if (sessionId != other.sessionId) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + dcpInstancePrefix.hashCode()
        __r = __r*31 + sessionId.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("DeleteSessionRequest (")
        printer.indent {
            print("dcpInstancePrefix = "); dcpInstancePrefix.print(printer); println()
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
 * #### Generated from [AspireWorkerModel.kt:88]
 */
data class DeleteSessionResponse (
    val sessionId: String?,
    val error: ErrorCode?
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeNullable(sessionId) { buffer.writeString(it) }
        buffer.writeNullable(error) { buffer.writeEnum(it) }
    }
    //companion
    
    companion object : IMarshaller<DeleteSessionResponse> {
        override val _type: KClass<DeleteSessionResponse> = DeleteSessionResponse::class
        override val id: RdId get() = RdId(-7588247750441437831)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): DeleteSessionResponse  {
            val sessionId = buffer.readNullable { buffer.readString() }
            val error = buffer.readNullable { buffer.readEnum<ErrorCode>() }
            return DeleteSessionResponse(sessionId, error)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: DeleteSessionResponse)  {
            value.write(ctx, buffer)
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
 * #### Generated from [AspireWorkerModel.kt:124]
 */
enum class ErrorCode {
    AspireAppHostNotFound, 
    UnsupportedLaunchConfigurationType, 
    AspireSessionNotFound, 
    DotNetProjectNotFound, 
    Unexpected;
    
    companion object : IMarshaller<ErrorCode> {
        val marshaller = FrameworkMarshallers.enum<ErrorCode>()
        
        
        override val _type: KClass<ErrorCode> = ErrorCode::class
        override val id: RdId get() = RdId(564443201287490)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ErrorCode {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ErrorCode)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * @property id The ID of the run session that the notification is related to
 * @property isStdErr True if the output comes from standard error stream, otherwise false (implying standard output stream)
 * @property message The text written by the service program
 * #### Generated from [AspireWorkerModel.kt:31]
 */
data class LogReceived (
    val id: String,
    val isStdErr: Boolean,
    val message: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(id)
        buffer.writeBool(isStdErr)
        buffer.writeString(message)
    }
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
            value.write(ctx, buffer)
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
 * #### Generated from [AspireWorkerModel.kt:43]
 */
enum class MessageLevel {
    Error, 
    Info, 
    Debug;
    
    companion object : IMarshaller<MessageLevel> {
        val marshaller = FrameworkMarshallers.enum<MessageLevel>()
        
        
        override val _type: KClass<MessageLevel> = MessageLevel::class
        override val id: RdId get() = RdId(-1438774601599785104)
        
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): MessageLevel {
            return marshaller.read(ctx, buffer)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: MessageLevel)  {
            marshaller.write(ctx, buffer, value)
        }
    }
}


/**
 * @property id The ID of the run session that the notification is related to
 * @property message The content of the message
 * @property error The error code. Only valid and required for error messages
 * #### Generated from [AspireWorkerModel.kt:40]
 */
data class MessageReceived (
    val id: String,
    val level: MessageLevel,
    val message: String,
    val error: ErrorCode?
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(id)
        buffer.writeEnum(level)
        buffer.writeString(message)
        buffer.writeNullable(error) { buffer.writeEnum(it) }
    }
    //companion
    
    companion object : IMarshaller<MessageReceived> {
        override val _type: KClass<MessageReceived> = MessageReceived::class
        override val id: RdId get() = RdId(7699071206331680373)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): MessageReceived  {
            val id = buffer.readString()
            val level = buffer.readEnum<MessageLevel>()
            val message = buffer.readString()
            val error = buffer.readNullable { buffer.readEnum<ErrorCode>() }
            return MessageReceived(id, level, message, error)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: MessageReceived)  {
            value.write(ctx, buffer)
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
        
        other as MessageReceived
        
        if (id != other.id) return false
        if (level != other.level) return false
        if (message != other.message) return false
        if (error != other.error) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + id.hashCode()
        __r = __r*31 + level.hashCode()
        __r = __r*31 + message.hashCode()
        __r = __r*31 + if (error != null) error.hashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("MessageReceived (")
        printer.indent {
            print("id = "); id.print(printer); println()
            print("level = "); level.print(printer); println()
            print("message = "); message.print(printer); println()
            print("error = "); error.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * @property id The ID of the run session that the notification is related to
 * @property pid The process ID of the service process associated with the run session
 * #### Generated from [AspireWorkerModel.kt:17]
 */
data class ProcessStarted (
    val id: String,
    val pid: Long
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(id)
        buffer.writeLong(pid)
    }
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
            value.write(ctx, buffer)
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
 * @property id The ID of the run session that the notification is related to
 * @property exitCode The exit code of the process associated with the run session
 * #### Generated from [AspireWorkerModel.kt:24]
 */
data class ProcessTerminated (
    val id: String,
    val exitCode: Int
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(id)
        buffer.writeInt(exitCode)
    }
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
            value.write(ctx, buffer)
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
 * #### Generated from [AspireWorkerModel.kt:54]
 */
data class SessionEnvironmentVariable (
    val key: String,
    val value: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(key)
        buffer.writeString(value)
    }
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
            value.write(ctx, buffer)
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
