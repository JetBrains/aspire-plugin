@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.intellij.aspire.generated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*
import com.jetbrains.rd.framework.impl.*

import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.string.*
import kotlin.reflect.KClass
import kotlin.jvm.JvmStatic



/**
 * #### Generated from [AspireSessionHostModel.kt:16]
 */
class AspireSessionHostModel private constructor(
    private val _sessions: RdMap<String, SessionModel>
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            serializers.register(EnvironmentVariableModel)
            serializers.register(SessionModel)
        }
        
        
        @JvmStatic
        @JvmName("internalCreateModel")
        @Deprecated("Use create instead", ReplaceWith("create(lifetime, protocol)"))
        internal fun createModel(lifetime: Lifetime, protocol: IProtocol): AspireSessionHostModel {
            @Suppress("DEPRECATION")
            return create(lifetime, protocol)
        }
        
        @JvmStatic
        @Deprecated("Use protocol.aspireSessionHostModel or revise the extension scope instead", ReplaceWith("protocol.aspireSessionHostModel"))
        fun create(lifetime: Lifetime, protocol: IProtocol): AspireSessionHostModel {
            AspireSessionHostRoot.register(protocol.serializers)
            
            return AspireSessionHostModel()
        }
        
        
        const val serializationHash = 4358981829313666140L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostModel
    override val serializationHash: Long get() = Companion.serializationHash
    
    //fields
    val sessions: IMutableViewableMap<String, SessionModel> get() = _sessions
    //methods
    //initializer
    init {
        _sessions.optimizeNested = true
    }
    
    init {
        bindableChildren.add("sessions" to _sessions)
    }
    
    //secondary constructor
    private constructor(
    ) : this(
        RdMap<String, SessionModel>(FrameworkMarshallers.String, SessionModel)
    )
    
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireSessionHostModel (")
        printer.indent {
            print("sessions = "); _sessions.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireSessionHostModel {
        return AspireSessionHostModel(
            _sessions.deepClonePolymorphic()
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
val IProtocol.aspireSessionHostModel get() = getOrCreateExtension(AspireSessionHostModel::class) { @Suppress("DEPRECATION") (AspireSessionHostModel.create(
    lifetime,
    this
)) }



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
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): EnvironmentVariableModel {
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
 * #### Generated from [AspireSessionHostModel.kt:22]
 */
data class SessionModel (
    val projectPath: String,
    val debug: Boolean,
    val envs: Array<EnvironmentVariableModel>?,
    val args: Array<String>?
) : IPrintable {
    //companion
    
    companion object : IMarshaller<SessionModel> {
        override val _type: KClass<SessionModel> = SessionModel::class
        
        @Suppress("UNCHECKED_CAST")
        override fun read(ctx: SerializationCtx, buffer: AbstractBuffer): SessionModel {
            val projectPath = buffer.readString()
            val debug = buffer.readBool()
            val envs = buffer.readNullable { buffer.readArray { EnvironmentVariableModel.read(ctx, buffer) } }
            val args = buffer.readNullable { buffer.readArray {buffer.readString()} }
            return SessionModel(projectPath, debug, envs, args)
        }
        
        override fun write(ctx: SerializationCtx, buffer: AbstractBuffer, value: SessionModel)  {
            buffer.writeString(value.projectPath)
            buffer.writeBool(value.debug)
            buffer.writeNullable(value.envs) { buffer.writeArray(it) { EnvironmentVariableModel.write(ctx, buffer, it) } }
            buffer.writeNullable(value.args) { buffer.writeArray(it) { buffer.writeString(it) } }
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
        if (envs != other.envs) return false
        if (args != other.args) return false
        
        return true
    }
    //hash code trait
    override fun hashCode(): Int  {
        var __r = 0
        __r = __r*31 + projectPath.hashCode()
        __r = __r*31 + debug.hashCode()
        __r = __r*31 + if (envs != null) envs.contentDeepHashCode() else 0
        __r = __r*31 + if (args != null) args.contentDeepHashCode() else 0
        return __r
    }
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("SessionModel (")
        printer.indent {
            print("projectPath = "); projectPath.print(printer); println()
            print("debug = "); debug.print(printer); println()
            print("envs = "); envs.print(printer); println()
            print("args = "); args.print(printer); println()
        }
        printer.print(")")
    }
    //deepClone
    //contexts
    //threading
}
