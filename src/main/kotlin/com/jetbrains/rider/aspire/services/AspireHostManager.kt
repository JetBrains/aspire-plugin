package com.jetbrains.rider.aspire.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireHostManager(private val project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<AspireHostManager>()

        private val LOG = logger<AspireHostManager>()
    }

    private val aspireHosts = ConcurrentHashMap<Path, AspireHost>()


    fun getAspireHosts(): List<AspireHost> {
        val hosts = mutableListOf<AspireHost>()
        for (host in aspireHosts) {
            hosts.add(host.value)
        }
        return hosts
    }

    fun getAspireHost(aspireHostProjectPath: Path) = aspireHosts[aspireHostProjectPath]

    fun getAspireResource(projectPath: Path): AspireResource? {
        for (aspireHost in aspireHosts) {
            val resource = aspireHost.value.getResource(projectPath) ?: continue
            return resource
        }

        return null
    }

    override fun dispose() {
    }
}