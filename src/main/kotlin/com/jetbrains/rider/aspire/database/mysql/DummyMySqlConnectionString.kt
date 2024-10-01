package com.jetbrains.rider.aspire.database.mysql

import com.intellij.database.util.common.isNotNullOrEmpty
import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.RdConnectionStringKeyValuePair
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.connectionStrings.ConnectionString
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.connectionStrings.DatabaseConnectionUrlProperty

class DummyMySqlConnectionString(project: Project, properties: List<RdConnectionStringKeyValuePair>) :
    ConnectionString(project, properties) {

    companion object {
        suspend fun parse(project: Project, connectionString: String): Result<DummyMySqlConnectionString> {
            return parseToProperties(project, connectionString).map { DummyMySqlConnectionString(project, it) }
        }
    }

    override fun getAllProperties(): Array<out DatabaseConnectionUrlProperty> {
        return Property.entries.toTypedArray()
    }

    override fun hasCredentials(): Boolean {
        if (username.isNotNullOrEmpty) return true
        if (password.isNotNullOrEmpty) return true
        return false
    }

    var database: String?
        get() = get(Property.Database)
        set(value) = set(Property.Database, value)

    @Suppress("MemberVisibilityCanBePrivate")
    var username: String?
        get() = get(Property.Username)
        set(value) = set(Property.Username, value)

    @Suppress("MemberVisibilityCanBePrivate")
    var password: String?
        get() = get(Property.Password)
        set(value) = set(Property.Password, value)

    enum class Property(private vararg val names: String) : DatabaseConnectionUrlProperty {
        Server("Server"),
        Port("Port"),
        Database("Database"),
        Username("User ID"),
        Password("Password");

        override fun getNames(): Array<out String> {
            return names
        }

        override fun getPrimaryName(): String {
            return names[0]
        }
    }
}