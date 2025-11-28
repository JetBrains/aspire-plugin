@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.util

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.aspire.generated.ResourceHealthStatus
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.dashboard.AspireResource
import icons.ReSharperIcons
import icons.RiderIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal fun getIcon(aspireResource: AspireResource): Icon {
    val baseIcon = getResourceIcon(aspireResource.type, aspireResource.containerImage?.value)

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

@ApiStatus.Internal
fun getResourceIcon(type: ResourceType, containerImage: String?) = when (type) {
    ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
    ResourceType.Executable -> AllIcons.Nodes.Console
    ResourceType.Parameter -> ReSharperIcons.PsiSymbols.Parameter
    ResourceType.ExternalService -> AllIcons.General.Web

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

    ResourceType.Postgres ->  AllIcons.Providers.Postgresql
    ResourceType.SqlServer -> AllIcons.Providers.SqlServer
    ResourceType.MySql -> AllIcons.Providers.Mysql
    ResourceType.MongoDB -> AllIcons.Providers.MongoDB

    ResourceType.Unknown -> AllIcons.FileTypes.Unknown
}