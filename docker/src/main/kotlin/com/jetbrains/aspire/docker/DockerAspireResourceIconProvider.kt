package com.jetbrains.aspire.docker

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.jetbrains.aspire.dashboard.AspireResourceIconProvider
import com.jetbrains.aspire.generated.ResourceType

internal class DockerAspireResourceIconProvider : AspireResourceIconProvider {
    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
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
        else -> null
    }
}