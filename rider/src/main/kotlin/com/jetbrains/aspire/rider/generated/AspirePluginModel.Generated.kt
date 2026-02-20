@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.jetbrains.aspire.rider.generated

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
    private val _getProjectOutputType: RdCall<com.jetbrains.rd.ide.model.RdPath, String?>,
    private val _referenceProjectsFromAppHost: RdCall<ReferenceProjectsFromAppHostRequest, ReferenceProjectsFromAppHostResponse?>,
    private val _referenceServiceDefaultsFromProjects: RdCall<ReferenceServiceDefaultsFromProjectsRequest, ReferenceServiceDefaultsFromProjectsResponse?>,
    private val _getReferencedProjectsFromAppHost: RdCall<GetReferencedProjectsFromAppHostRequest, GetReferencedProjectsFromAppHostResponse?>,
    private val _startAspireHost: RdCall<StartAspireHostRequest, StartAspireHostResponse>,
    private val _stopAspireHost: RdCall<StopAspireHostRequest, Unit>,
    private val _unitTestRunCancelled: RdSignal<String>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            val classLoader = javaClass.classLoader
            serializers.register(LazyCompanionMarshaller(RdId(-4748479939684696606), classLoader, "com.jetbrains.aspire.rider.generated.ReferenceProjectsFromAppHostRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(371074459503752014), classLoader, "com.jetbrains.aspire.rider.generated.ReferenceProjectsFromAppHostResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(8169256003235604124), classLoader, "com.jetbrains.aspire.rider.generated.ReferenceServiceDefaultsFromProjectsRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-5007480931577060908), classLoader, "com.jetbrains.aspire.rider.generated.ReferenceServiceDefaultsFromProjectsResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-2857445708302516858), classLoader, "com.jetbrains.aspire.rider.generated.GetReferencedProjectsFromAppHostRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(3652903411222669354), classLoader, "com.jetbrains.aspire.rider.generated.GetReferencedProjectsFromAppHostResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(4011588048384607098), classLoader, "com.jetbrains.aspire.rider.generated.StartAspireHostRequest"))
            serializers.register(LazyCompanionMarshaller(RdId(-7704547362275130218), classLoader, "com.jetbrains.aspire.rider.generated.AspireHostEnvironmentVariable"))
            serializers.register(LazyCompanionMarshaller(RdId(-4767979015991107402), classLoader, "com.jetbrains.aspire.rider.generated.StartAspireHostResponse"))
            serializers.register(LazyCompanionMarshaller(RdId(-2676024516763561580), classLoader, "com.jetbrains.aspire.rider.generated.StopAspireHostRequest"))
        }
        
        
        
        
        private val __StringNullableSerializer = FrameworkMarshallers.String.nullable()
        private val __ReferenceProjectsFromAppHostResponseNullableSerializer = ReferenceProjectsFromAppHostResponse.nullable()
        private val __ReferenceServiceDefaultsFromProjectsResponseNullableSerializer = ReferenceServiceDefaultsFromProjectsResponse.nullable()
        private val __GetReferencedProjectsFromAppHostResponseNullableSerializer = GetReferencedProjectsFromAppHostResponse.nullable()
        
        const val serializationHash = -6437809188999994001L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspirePluginModel
    override val serializationHash: Long get() = AspirePluginModel.serializationHash
    
    //fields
    val getProjectOutputType: IRdCall<com.jetbrains.rd.ide.model.RdPath, String?> get() = _getProjectOutputType
    val referenceProjectsFromAppHost: IRdCall<ReferenceProjectsFromAppHostRequest, ReferenceProjectsFromAppHostResponse?> get() = _referenceProjectsFromAppHost
    val referenceServiceDefaultsFromProjects: IRdCall<ReferenceServiceDefaultsFromProjectsRequest, ReferenceServiceDefaultsFromProjectsResponse?> get() = _referenceServiceDefaultsFromProjects
    val getReferencedProjectsFromAppHost: IRdCall<GetReferencedProjectsFromAppHostRequest, GetReferencedProjectsFromAppHostResponse?> get() = _getReferencedProjectsFromAppHost
    val startAspireHost: IRdEndpoint<StartAspireHostRequest, StartAspireHostResponse> get() = _startAspireHost
    val stopAspireHost: IRdEndpoint<StopAspireHostRequest, Unit> get() = _stopAspireHost
    val unitTestRunCancelled: IAsyncSource<String> get() = _unitTestRunCancelled
    //methods
    //initializer
    init {
        _startAspireHost.async = true
        _stopAspireHost.async = true
        _unitTestRunCancelled.async = true
    }
    
    init {
        bindableChildren.add("getProjectOutputType" to _getProjectOutputType)
        bindableChildren.add("referenceProjectsFromAppHost" to _referenceProjectsFromAppHost)
        bindableChildren.add("referenceServiceDefaultsFromProjects" to _referenceServiceDefaultsFromProjects)
        bindableChildren.add("getReferencedProjectsFromAppHost" to _getReferencedProjectsFromAppHost)
        bindableChildren.add("startAspireHost" to _startAspireHost)
        bindableChildren.add("stopAspireHost" to _stopAspireHost)
        bindableChildren.add("unitTestRunCancelled" to _unitTestRunCancelled)
    }
    
    //secondary constructor
    internal constructor(
    ) : this(
        RdCall<com.jetbrains.rd.ide.model.RdPath, String?>(com.jetbrains.rd.ide.model.RdPath, __StringNullableSerializer),
        RdCall<ReferenceProjectsFromAppHostRequest, ReferenceProjectsFromAppHostResponse?>(ReferenceProjectsFromAppHostRequest, __ReferenceProjectsFromAppHostResponseNullableSerializer),
        RdCall<ReferenceServiceDefaultsFromProjectsRequest, ReferenceServiceDefaultsFromProjectsResponse?>(ReferenceServiceDefaultsFromProjectsRequest, __ReferenceServiceDefaultsFromProjectsResponseNullableSerializer),
        RdCall<GetReferencedProjectsFromAppHostRequest, GetReferencedProjectsFromAppHostResponse?>(GetReferencedProjectsFromAppHostRequest, __GetReferencedProjectsFromAppHostResponseNullableSerializer),
        RdCall<StartAspireHostRequest, StartAspireHostResponse>(StartAspireHostRequest, StartAspireHostResponse),
        RdCall<StopAspireHostRequest, Unit>(StopAspireHostRequest, FrameworkMarshallers.Void),
        RdSignal<String>(FrameworkMarshallers.String)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspirePluginModel (")
        printer.indent {
            print("getProjectOutputType = "); _getProjectOutputType.print(printer); println()
            print("referenceProjectsFromAppHost = "); _referenceProjectsFromAppHost.print(printer); println()
            print("referenceServiceDefaultsFromProjects = "); _referenceServiceDefaultsFromProjects.print(printer); println()
            print("getReferencedProjectsFromAppHost = "); _getReferencedProjectsFromAppHost.print(printer); println()
            print("startAspireHost = "); _startAspireHost.print(printer); println()
            print("stopAspireHost = "); _stopAspireHost.print(printer); println()
            print("unitTestRunCancelled = "); _unitTestRunCancelled.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspirePluginModel   {
        return AspirePluginModel(
            _getProjectOutputType.deepClonePolymorphic(),
            _referenceProjectsFromAppHost.deepClonePolymorphic(),
            _referenceServiceDefaultsFromProjects.deepClonePolymorphic(),
            _getReferencedProjectsFromAppHost.deepClonePolymorphic(),
            _startAspireHost.deepClonePolymorphic(),
            _stopAspireHost.deepClonePolymorphic(),
            _unitTestRunCancelled.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val com.jetbrains.rd.ide.model.Solution.aspirePluginModel get() = getOrCreateExtension("aspirePluginModel", ::AspirePluginModel)



/**
 * #### Generated from [AspirePluginModel.kt:45]
 */
data class AspireHostEnvironmentVariable (
    val key: String,
    val value: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(key)
        buffer.writeString(value)
    }
    //companion
    
    companion object : IMarshaller<AspireHostEnvironmentVariable> {
        override val _type: KClass<AspireHostEnvironmentVariable> = AspireHostEnvironmentVariable::class
        override val id: RdId get() = RdId(-7704547362275130218)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): AspireHostEnvironmentVariable  {
            val key = buffer.readString()
            val value = buffer.readString()
            return AspireHostEnvironmentVariable(key, value)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: AspireHostEnvironmentVariable)  {
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
        
        other as AspireHostEnvironmentVariable
        
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
        printer.println("AspireHostEnvironmentVariable (")
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
 * #### Generated from [AspirePluginModel.kt:30]
 */
data class GetReferencedProjectsFromAppHostRequest (
    val hostProjectFilePath: com.jetbrains.rd.ide.model.RdPath,
    val projectFilePaths: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, hostProjectFilePath)
        buffer.writeList(projectFilePaths) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<GetReferencedProjectsFromAppHostRequest> {
        override val _type: KClass<GetReferencedProjectsFromAppHostRequest> = GetReferencedProjectsFromAppHostRequest::class
        override val id: RdId get() = RdId(-2857445708302516858)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetReferencedProjectsFromAppHostRequest  {
            val hostProjectFilePath = com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer)
            val projectFilePaths = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return GetReferencedProjectsFromAppHostRequest(hostProjectFilePath, projectFilePaths)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetReferencedProjectsFromAppHostRequest)  {
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
        
        other as GetReferencedProjectsFromAppHostRequest
        
        if (hostProjectFilePath != other.hostProjectFilePath) return false
        if (projectFilePaths != other.projectFilePaths) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + hostProjectFilePath.hashCode()
        __r = __r*31 + projectFilePaths.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("GetReferencedProjectsFromAppHostRequest (")
        printer.indent {
            print("hostProjectFilePath = "); hostProjectFilePath.print(printer); println()
            print("projectFilePaths = "); projectFilePaths.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:35]
 */
data class GetReferencedProjectsFromAppHostResponse (
    val referencedProjectFilePaths: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeList(referencedProjectFilePaths) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<GetReferencedProjectsFromAppHostResponse> {
        override val _type: KClass<GetReferencedProjectsFromAppHostResponse> = GetReferencedProjectsFromAppHostResponse::class
        override val id: RdId get() = RdId(3652903411222669354)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): GetReferencedProjectsFromAppHostResponse  {
            val referencedProjectFilePaths = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return GetReferencedProjectsFromAppHostResponse(referencedProjectFilePaths)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: GetReferencedProjectsFromAppHostResponse)  {
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
        
        other as GetReferencedProjectsFromAppHostResponse
        
        if (referencedProjectFilePaths != other.referencedProjectFilePaths) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + referencedProjectFilePaths.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("GetReferencedProjectsFromAppHostResponse (")
        printer.indent {
            print("referencedProjectFilePaths = "); referencedProjectFilePaths.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:12]
 */
data class ReferenceProjectsFromAppHostRequest (
    val hostProjectFilePath: com.jetbrains.rd.ide.model.RdPath,
    val projectFilePaths: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, hostProjectFilePath)
        buffer.writeList(projectFilePaths) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<ReferenceProjectsFromAppHostRequest> {
        override val _type: KClass<ReferenceProjectsFromAppHostRequest> = ReferenceProjectsFromAppHostRequest::class
        override val id: RdId get() = RdId(-4748479939684696606)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ReferenceProjectsFromAppHostRequest  {
            val hostProjectFilePath = com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer)
            val projectFilePaths = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return ReferenceProjectsFromAppHostRequest(hostProjectFilePath, projectFilePaths)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ReferenceProjectsFromAppHostRequest)  {
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
        
        other as ReferenceProjectsFromAppHostRequest
        
        if (hostProjectFilePath != other.hostProjectFilePath) return false
        if (projectFilePaths != other.projectFilePaths) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + hostProjectFilePath.hashCode()
        __r = __r*31 + projectFilePaths.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ReferenceProjectsFromAppHostRequest (")
        printer.indent {
            print("hostProjectFilePath = "); hostProjectFilePath.print(printer); println()
            print("projectFilePaths = "); projectFilePaths.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:17]
 */
data class ReferenceProjectsFromAppHostResponse (
    val referencedProjectFilePaths: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeList(referencedProjectFilePaths) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<ReferenceProjectsFromAppHostResponse> {
        override val _type: KClass<ReferenceProjectsFromAppHostResponse> = ReferenceProjectsFromAppHostResponse::class
        override val id: RdId get() = RdId(371074459503752014)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ReferenceProjectsFromAppHostResponse  {
            val referencedProjectFilePaths = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return ReferenceProjectsFromAppHostResponse(referencedProjectFilePaths)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ReferenceProjectsFromAppHostResponse)  {
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
        
        other as ReferenceProjectsFromAppHostResponse
        
        if (referencedProjectFilePaths != other.referencedProjectFilePaths) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + referencedProjectFilePaths.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ReferenceProjectsFromAppHostResponse (")
        printer.indent {
            print("referencedProjectFilePaths = "); referencedProjectFilePaths.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:21]
 */
data class ReferenceServiceDefaultsFromProjectsRequest (
    val sharedProjectFilePath: com.jetbrains.rd.ide.model.RdPath,
    val projectFilePaths: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, sharedProjectFilePath)
        buffer.writeList(projectFilePaths) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<ReferenceServiceDefaultsFromProjectsRequest> {
        override val _type: KClass<ReferenceServiceDefaultsFromProjectsRequest> = ReferenceServiceDefaultsFromProjectsRequest::class
        override val id: RdId get() = RdId(8169256003235604124)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ReferenceServiceDefaultsFromProjectsRequest  {
            val sharedProjectFilePath = com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer)
            val projectFilePaths = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return ReferenceServiceDefaultsFromProjectsRequest(sharedProjectFilePath, projectFilePaths)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ReferenceServiceDefaultsFromProjectsRequest)  {
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
        
        other as ReferenceServiceDefaultsFromProjectsRequest
        
        if (sharedProjectFilePath != other.sharedProjectFilePath) return false
        if (projectFilePaths != other.projectFilePaths) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + sharedProjectFilePath.hashCode()
        __r = __r*31 + projectFilePaths.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ReferenceServiceDefaultsFromProjectsRequest (")
        printer.indent {
            print("sharedProjectFilePath = "); sharedProjectFilePath.print(printer); println()
            print("projectFilePaths = "); projectFilePaths.print(printer); println()
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
data class ReferenceServiceDefaultsFromProjectsResponse (
    val projectFilePathsWithReference: List<com.jetbrains.rd.ide.model.RdPath>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeList(projectFilePathsWithReference) { v -> com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, v) }
    }
    //companion
    
    companion object : IMarshaller<ReferenceServiceDefaultsFromProjectsResponse> {
        override val _type: KClass<ReferenceServiceDefaultsFromProjectsResponse> = ReferenceServiceDefaultsFromProjectsResponse::class
        override val id: RdId get() = RdId(-5007480931577060908)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): ReferenceServiceDefaultsFromProjectsResponse  {
            val projectFilePathsWithReference = buffer.readList { com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer) }
            return ReferenceServiceDefaultsFromProjectsResponse(projectFilePathsWithReference)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: ReferenceServiceDefaultsFromProjectsResponse)  {
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
        
        other as ReferenceServiceDefaultsFromProjectsResponse
        
        if (projectFilePathsWithReference != other.projectFilePathsWithReference) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + projectFilePathsWithReference.hashCode()
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("ReferenceServiceDefaultsFromProjectsResponse (")
        printer.indent {
            print("projectFilePathsWithReference = "); projectFilePathsWithReference.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}


/**
 * #### Generated from [AspirePluginModel.kt:39]
 */
data class StartAspireHostRequest (
    val unitTestRunId: String,
    val aspireHostProjectPath: com.jetbrains.rd.ide.model.RdPath,
    val underDebugger: Boolean
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(unitTestRunId)
        com.jetbrains.rd.ide.model.RdPath.write(ctx, buffer, aspireHostProjectPath)
        buffer.writeBool(underDebugger)
    }
    //companion
    
    companion object : IMarshaller<StartAspireHostRequest> {
        override val _type: KClass<StartAspireHostRequest> = StartAspireHostRequest::class
        override val id: RdId get() = RdId(4011588048384607098)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StartAspireHostRequest  {
            val unitTestRunId = buffer.readString()
            val aspireHostProjectPath = com.jetbrains.rd.ide.model.RdPath.read(ctx, buffer)
            val underDebugger = buffer.readBool()
            return StartAspireHostRequest(unitTestRunId, aspireHostProjectPath, underDebugger)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StartAspireHostRequest)  {
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
        
        other as StartAspireHostRequest
        
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
        printer.println("StartAspireHostRequest (")
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
 * #### Generated from [AspirePluginModel.kt:50]
 */
data class StartAspireHostResponse (
    val environmentVariables: Array<AspireHostEnvironmentVariable>
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeArray(environmentVariables) { AspireHostEnvironmentVariable.write(ctx, buffer, it) }
    }
    //companion
    
    companion object : IMarshaller<StartAspireHostResponse> {
        override val _type: KClass<StartAspireHostResponse> = StartAspireHostResponse::class
        override val id: RdId get() = RdId(-4767979015991107402)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StartAspireHostResponse  {
            val environmentVariables = buffer.readArray {AspireHostEnvironmentVariable.read(ctx, buffer)}
            return StartAspireHostResponse(environmentVariables)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StartAspireHostResponse)  {
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
        
        other as StartAspireHostResponse
        
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
        printer.println("StartAspireHostResponse (")
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
 * #### Generated from [AspirePluginModel.kt:54]
 */
data class StopAspireHostRequest (
    val unitTestRunId: String
) : IPrintable {
    //write-marshaller
    private fun write(ctx: SerializationCtx, buffer: AbstractBuffer)  {
        buffer.writeString(unitTestRunId)
    }
    //companion
    
    companion object : IMarshaller<StopAspireHostRequest> {
        override val _type: KClass<StopAspireHostRequest> = StopAspireHostRequest::class
        override val id: RdId get() = RdId(-2676024516763561580)
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): StopAspireHostRequest  {
            val unitTestRunId = buffer.readString()
            return StopAspireHostRequest(unitTestRunId)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: StopAspireHostRequest)  {
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
        
        other as StopAspireHostRequest
        
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
        printer.println("StopAspireHostRequest (")
        printer.indent {
            print("unitTestRunId = "); unitTestRunId.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
