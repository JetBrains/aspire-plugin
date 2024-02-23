package me.rafaelldi.aspire

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class AspireService(val scope: CoroutineScope): Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<AspireService>()
    }

    override fun dispose() {
    }
}