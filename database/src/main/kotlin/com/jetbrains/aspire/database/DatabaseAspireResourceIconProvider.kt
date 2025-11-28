package com.jetbrains.aspire.database

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.dashboard.AspireResourceIconProviderExtension
import com.jetbrains.aspire.generated.ResourceType

internal class DatabaseAspireResourceIconProvider : AspireResourceIconProviderExtension {
    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Postgres -> AllIcons.Providers.Postgresql
        ResourceType.SqlServer -> AllIcons.Providers.SqlServer
        ResourceType.MySql -> AllIcons.Providers.Mysql
        ResourceType.MongoDB -> AllIcons.Providers.MongoDB
        else -> null
    }
}