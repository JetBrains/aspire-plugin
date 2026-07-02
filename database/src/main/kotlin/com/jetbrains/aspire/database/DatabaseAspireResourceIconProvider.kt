package com.jetbrains.aspire.database

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceType

internal class DatabaseAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 3

    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Postgres -> AllIcons.Providers.Postgresql
        ResourceType.SqlServer -> AllIcons.Providers.SqlServer
        ResourceType.MySql -> AllIcons.Providers.Mysql
        ResourceType.MongoDB -> AllIcons.Providers.MongoDB
        ResourceType.Container -> when {
            containerImage?.contains("postgres") == true -> AllIcons.Providers.Postgresql
            containerImage?.contains("mssql") == true -> AllIcons.Providers.SqlServer
            containerImage?.contains("mysql") == true -> AllIcons.Providers.Mysql
            containerImage?.contains("mongo") == true -> AllIcons.Providers.MongoDB
            containerImage?.contains("elasticsearch") == true -> AllIcons.Providers.Elasticsearch
            containerImage?.contains("redis") == true -> AllIcons.Providers.Redis
            containerImage?.contains("rabbitmq") == true -> AllIcons.Providers.RabbitMQ
            containerImage?.contains("confluent") == true -> AllIcons.Providers.Kafka
            else -> null
        }

        else -> null
    }
}
