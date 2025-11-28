package com.jetbrains.aspire.databases

import com.intellij.database.Dbms
import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.DotnetDataProvider

internal class DummyMongoDataProvider : DotnetDataProvider() {
    override val providerId: String = "Mongo"
    override val dbms: Collection<Dbms> = listOf(Dbms.MONGO)

    companion object {
        fun getInstance(project: Project): DummyMongoDataProvider {
            return getInstance<DummyMongoDataProvider>(project)
        }
    }
}