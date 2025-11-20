package com.jetbrains.rider.aspire.run.file

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.ProtocolLifetimedSettingsEditor
import javax.swing.JComponent

internal class AspireFileConfigurationSettingsEditor(private val project: Project) :
    ProtocolLifetimedSettingsEditor<AspireFileConfiguration>() {
    override fun createEditor(lifetime: Lifetime): JComponent {
        TODO("Not yet implemented")
    }

    override fun resetEditorFrom(p0: AspireFileConfiguration) {
        TODO("Not yet implemented")
    }

    override fun applyEditorTo(p0: AspireFileConfiguration) {
        TODO("Not yet implemented")
    }
}