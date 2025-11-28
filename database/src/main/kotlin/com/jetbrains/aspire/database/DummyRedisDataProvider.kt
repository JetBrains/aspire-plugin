package com.jetbrains.aspire.database

import com.intellij.database.Dbms
import com.intellij.database.dialects.redis.RedisDbms
import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.DotnetDataProvider

internal class DummyRedisDataProvider : DotnetDataProvider() {
    override val providerId: String = "Redis"
    override val dbms: Collection<Dbms> = listOf(RedisDbms.REDIS)

    companion object {
        fun getInstance(project: Project): DummyRedisDataProvider {
            return getInstance<DummyRedisDataProvider>(project)
        }
    }
}