package com.jetbrains.rider.aspire.services.a

import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.ResourceEnvironmentVariable
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceStateStyle
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.generated.ResourceUrl
import kotlinx.datetime.LocalDateTime
import java.nio.file.Path

class AspireResource(
    private val project: Project
) {

    val serviceViewContributor: AspireResourceServiceViewContributor by lazy {
        AspireResourceServiceViewContributor(this)
    }

    var uid: String
        private set
    var name: String
        private set
    var type: ResourceType
        private set
    var displayName: String
        private set
    var state: ResourceState?
        private set
    var stateStyle: ResourceStateStyle?
        private set
    var urls: Array<ResourceUrl>
        private set
    var environment: Array<ResourceEnvironmentVariable>
        private set

    var serviceInstanceId: String? = null
        private set

    var startTime: LocalDateTime? = null
        private set
    var exitCode: Int? = null
        private set
    var pid: Int? = null
        private set
    var projectPath: Path? = null
        private set
    var executablePath: Path? = null
        private set
    var executableWorkDir: Path? = null
        private set
    var args: String? = null
        private set
    var containerImage: String? = null
        private set
    var containerId: String? = null
        private set
    var containerPorts: String? = null
        private set
    var containerCommand: String? = null
        private set
    var containerArgs: String? = null
        private set

    init {
        uid = ""
        name = ""
        type = ResourceType.Unknown
        displayName = ""
        state = ResourceState.Unknown
        stateStyle = ResourceStateStyle.Unknown
        urls = emptyArray()
        environment = emptyArray()
    }
}