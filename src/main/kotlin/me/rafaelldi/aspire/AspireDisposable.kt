package me.rafaelldi.aspire

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AspireDisposable : Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<AspireDisposable>()
    }

    override fun dispose() {
    }
}