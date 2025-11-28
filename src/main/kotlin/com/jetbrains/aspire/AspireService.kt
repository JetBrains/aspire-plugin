package com.jetbrains.aspire

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class AspireService(val scope: CoroutineScope): Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<AspireService>()
    }

    private val lifetimeDef = LifetimeDefinition()

    val lifetime = lifetimeDef.lifetime

    override fun dispose() {
        lifetimeDef.terminate()
    }
}