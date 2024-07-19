package model.aspirePlugin

import com.jetbrains.rd.generator.nova.Ext
import com.jetbrains.rd.generator.nova.csharp.CSharp50Generator
import com.jetbrains.rd.generator.nova.kotlin.Kotlin11Generator
import com.jetbrains.rd.generator.nova.setting
import com.jetbrains.rider.model.nova.ide.SolutionModel

@Suppress("unused")
object AspirePluginModel : Ext(SolutionModel.Solution) {
    init {
        setting(Kotlin11Generator.Namespace, "me.rafaelldi.aspire.generated")
        setting(CSharp50Generator.Namespace, "AspirePlugin.Generated")
    }
}