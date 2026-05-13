package com.jetbrains.aspire.docker

import com.intellij.docker.DockerIcons
import com.jetbrains.aspire.dashboard.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceType

internal class DockerAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 1

    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Container -> DockerIcons.Docker
        else -> null
    }
}