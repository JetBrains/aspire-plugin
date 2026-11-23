package com.jetbrains.aspire.util

import com.jetbrains.aspire.worker.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
fun AspireAppHost.getAllResources(): List<AspireResource> =
    rootResources.value.flatMap { it.withDescendants() }

private fun AspireResource.withDescendants(): List<AspireResource> =
    listOf(this) + childrenResources.value.flatMap { it.withDescendants() }

@ApiStatus.Internal
fun AspireAppHost.findResource(predicate: (AspireResource) -> Boolean): AspireResource? =
    rootResources.value.findInTree(predicate)

@ApiStatus.Internal
fun AspireWorker.findProjectResource(projectPath: Path): AspireResource? =
    appHosts.value.firstNotNullOfOrNull { host ->
        host.findResource {
            val data = it.data.value
            data.kind == ResourceKind.Project && data.projectPath?.value?.toNioPath() == projectPath
        }
    }

private fun List<AspireResource>.findInTree(predicate: (AspireResource) -> Boolean): AspireResource? {
    for (resource in this) {
        if (predicate(resource)) return resource
    }

    for (resource in this) {
        val found = resource.childrenResources.value.findInTree(predicate)
        if (found != null) return found
    }

    return null
}

internal fun mapResourceKind(type: String): ResourceKind = when (type) {
    "Project" -> ResourceKind.Project
    "Container" -> ResourceKind.Container
    "Executable" -> ResourceKind.Executable
    "Parameter" -> ResourceKind.Parameter
    "ExternalService" -> ResourceKind.ExternalService
    "MongoDBDatabaseResource" -> ResourceKind.MongoDB
    "MySqlDatabaseResource" -> ResourceKind.MySql
    "PostgresDatabaseResource" -> ResourceKind.Postgres
    "SqlServerDatabaseResource" -> ResourceKind.SqlServer
    "AzureStorageResource" -> ResourceKind.AzureStorageResource
    else -> ResourceKind.Unknown
}

internal fun mapLaunchConfigurationType(type: String): ResourceLaunchConfigurationType = when (type) {
    "project", "project-with-external-build.v1" -> ResourceLaunchConfigurationType.Project
    "azure-functions" -> ResourceLaunchConfigurationType.AzureFunctions
    else -> ResourceLaunchConfigurationType.Unknown
}

internal fun mapResourceState(state: String): ResourceState = when (state) {
    "Building" -> ResourceState.Building
    "Starting" -> ResourceState.Starting
    "Running" -> ResourceState.Running
    "FailedToStart" -> ResourceState.FailedToStart
    "RuntimeUnhealthy" -> ResourceState.RuntimeUnhealthy
    "Stopping" -> ResourceState.Stopping
    "Exited" -> ResourceState.Exited
    "Finished" -> ResourceState.Finished
    "Waiting" -> ResourceState.Waiting
    "NotStarted" -> ResourceState.NotStarted
    "Hidden" -> ResourceState.Hidden
    else -> ResourceState.Unknown
}

internal fun mapResourceStateStyle(style: String): ResourceStateStyle = when (style) {
    "success" -> ResourceStateStyle.Success
    "info" -> ResourceStateStyle.Info
    "warning" -> ResourceStateStyle.Warning
    "error" -> ResourceStateStyle.Error
    else -> ResourceStateStyle.Unknown
}
