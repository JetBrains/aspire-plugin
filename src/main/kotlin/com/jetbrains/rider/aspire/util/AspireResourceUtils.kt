@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.util

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.rider.aspire.generated.ResourceHealthStatus
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import icons.RiderIcons
import javax.swing.Icon

internal fun getIcon(aspireResource: AspireResource): Icon {
    val type = aspireResource.type
    val state = aspireResource.state
    val healthStatus = aspireResource.healthStatus

    var icon = when (type) {
        ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
        ResourceType.Container -> when {
            aspireResource.containerImage?.contains("postgres") == true -> AllIcons.Providers.Postgresql
            aspireResource.containerImage?.contains("mssql") == true -> AllIcons.Providers.SqlServer
            aspireResource.containerImage?.contains("mysql") == true -> AllIcons.Providers.Mysql
            aspireResource.containerImage?.contains("mongo") == true -> AllIcons.Providers.MongoDB
            aspireResource.containerImage?.contains("elasticsearch") == true -> AllIcons.Providers.Elasticsearch
            aspireResource.containerImage?.contains("redis") == true -> AllIcons.Providers.Redis
            aspireResource.containerImage?.contains("rabbitmq") == true -> AllIcons.Providers.RabbitMQ
            aspireResource.containerImage?.contains("confluent") == true -> AllIcons.Providers.Kafka
            else -> DockerIcons.Docker
        }
        ResourceType.Executable -> AllIcons.Nodes.Console
        ResourceType.Unknown -> AllIcons.RunConfigurations.TestUnknown
    }

    icon = when(state) {
        ResourceState.Exited -> icon
        ResourceState.Finished -> icon
        ResourceState.FailedToStart -> BadgeIconSupplier(icon).errorIcon
        ResourceState.Running -> {
            if (healthStatus == ResourceHealthStatus.Healthy || healthStatus == null) {
                BadgeIconSupplier(icon).liveIndicatorIcon
            } else {
                BadgeIconSupplier(icon).warningIcon
            }
        }
        else -> icon
    }

    return icon
}