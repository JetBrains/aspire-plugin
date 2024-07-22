package me.rafaelldi.aspire.unitTests

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.generated.SessionHostModel

@Service(Service.Level.PROJECT)
class AspireUnitTestService(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireUnitTestService = project.service()
        private val LOG = logger<AspireUnitTestService>()
    }

    fun startSessionHost(lifetime: Lifetime, model: SessionHostModel, rdTask: RdTask<Unit>) {
        scope.launch(Dispatchers.Default) {
            lifetimedCoroutineScope(lifetime) {
                LOG.trace("Starting a session host for a unit test session")
                rdTask.set(Unit)
            }
        }
    }
}