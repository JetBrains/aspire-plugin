package com.jetbrains.aspire.database

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceKind

internal class DatabaseAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 3

    override fun getIcon(
        type: ResourceKind,
        configurationType: ResourceLaunchConfigurationType,
        containerImage: String?
    ) = when (type) {
        ResourceKind.Postgres -> AllIcons.Providers.Postgresql
        ResourceKind.SqlServer -> AllIcons.Providers.SqlServer
        ResourceKind.MySql -> AllIcons.Providers.Mysql
        ResourceKind.MongoDB -> AllIcons.Providers.MongoDB
        ResourceKind.Container -> when {
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
