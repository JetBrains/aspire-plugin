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
    val baseIcon = getBaseIcon(aspireResource.type, aspireResource.containerImage)

    val icon = when (aspireResource.state) {
        ResourceState.FailedToStart -> BadgeIconSupplier(baseIcon).errorIcon
        ResourceState.RuntimeUnhealthy -> BadgeIconSupplier(baseIcon).errorIcon
        ResourceState.Waiting -> BadgeIconSupplier(baseIcon).warningIcon
        ResourceState.Running -> {
            if (aspireResource.healthStatus == ResourceHealthStatus.Healthy || aspireResource.healthStatus == null) {
                BadgeIconSupplier(baseIcon).liveIndicatorIcon
            } else {
                BadgeIconSupplier(baseIcon).warningIcon
            }
        }

        else -> baseIcon
    }

    return icon
}

internal fun getBaseIcon(type: ResourceType, containerImage: String?) = when (type) {
    ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
    ResourceType.Container -> when {
        containerImage?.contains("postgres") == true -> AllIcons.Providers.Postgresql
        containerImage?.contains("mssql") == true -> AllIcons.Providers.SqlServer
        containerImage?.contains("mysql") == true -> AllIcons.Providers.Mysql
        containerImage?.contains("mongo") == true -> AllIcons.Providers.MongoDB
        containerImage?.contains("elasticsearch") == true -> AllIcons.Providers.Elasticsearch
        containerImage?.contains("redis") == true -> AllIcons.Providers.Redis
        containerImage?.contains("rabbitmq") == true -> AllIcons.Providers.RabbitMQ
        containerImage?.contains("confluent") == true -> AllIcons.Providers.Kafka
        else -> DockerIcons.Docker
    }

    ResourceType.Executable -> AllIcons.Nodes.Console
    ResourceType.Unknown -> AllIcons.RunConfigurations.Application
}