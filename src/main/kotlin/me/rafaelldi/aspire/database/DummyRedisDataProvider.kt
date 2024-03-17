package me.rafaelldi.aspire.database

import com.intellij.database.Dbms
import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.DotnetDataProvider

class DummyRedisDataProvider : DotnetDataProvider() {
    override val providerId: String = "Redis"
    override val dbms: Collection<Dbms> = listOf(Dbms.REDIS)

    companion object {
        fun getInstance(project: Project): DummyRedisDataProvider {
            return getInstance<DummyRedisDataProvider>(project)
        }
    }
}