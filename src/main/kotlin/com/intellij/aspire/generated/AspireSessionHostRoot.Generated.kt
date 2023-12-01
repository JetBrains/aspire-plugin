@file:Suppress("EXPERIMENTAL_API_USAGE","EXPERIMENTAL_UNSIGNED_LITERALS","PackageDirectoryMismatch","UnusedImport","unused","LocalVariableName","CanBeVal","PropertyName","EnumEntryName","ClassName","ObjectPropertyName","UnnecessaryVariable","SpellCheckingInspection")
package com.intellij.aspire.generated

import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.base.*

import com.jetbrains.rd.util.string.*


/**
 * #### Generated from [AspireSessionHostModel.kt:8]
 */
class AspireSessionHostRoot private constructor(
) : RdExtBase() {
    //companion
    
    companion object : ISerializersOwner {
        
        override fun registerSerializersCore(serializers: ISerializers)  {
            AspireSessionHostRoot.register(serializers)
            AspireSessionHostModel.register(serializers)
        }
        
        
        
        
        
        const val serializationHash = -8489210090490017120L
        
    }
    override val serializersOwner: ISerializersOwner get() = AspireSessionHostRoot
    override val serializationHash: Long get() = Companion.serializationHash
    
    //fields
    //methods
    //initializer
    //secondary constructor
    //equals trait
    //hash code trait
    //pretty print
    override fun print(printer: PrettyPrinter)  {
        printer.println("AspireSessionHostRoot (")
        printer.print(")")
    }
    //deepClone
    override fun deepClone(): AspireSessionHostRoot {
        return AspireSessionHostRoot(
        )
    }
    //contexts
    //threading
    override val extThreading: ExtThreadingKind get() = ExtThreadingKind.Default
}
