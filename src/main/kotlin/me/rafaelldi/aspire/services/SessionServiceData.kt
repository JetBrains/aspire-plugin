package me.rafaelldi.aspire.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.flow.Flow
import me.rafaelldi.aspire.AspireDisposable
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.sessionHost.AspireSessionLog

class SessionServiceData(
    val sessionModel: SessionModel,
    val sessionLifetime: Lifetime,
    val sessionEvents: Flow<AspireSessionLog>,
    val project: Project
) {
    private val descriptor by lazy {
        val descriptor = SessionServiceViewDescriptor(this, project)
        Disposer.register(AspireDisposable.getInstance(project), descriptor)
        descriptor
    }

    fun getViewDescriptor() = descriptor
}