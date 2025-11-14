package com.jetbrains.rider.aspire.databases.mysql

import com.intellij.database.util.common.isNotNullOrEmpty
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.connectionStrings.DatabaseConnectionUrlProperty
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.jdbcUrls.JdbcUrl
import java.net.URLEncoder
import java.sql.DriverPropertyInfo

internal class DummyMySqlJdbcUrl(properties: Collection<DriverPropertyInfo>) : JdbcUrl(properties) {
    constructor() : this(emptyList())

    override suspend fun build(): Result<String> {
        val builder = StringBuilder("jdbc:mysql://")

        serverName?.let { builder.append(it) }
        port?.let { builder.append(":$it") }
        builder.append('/')
        database?.let { builder.append(urlEncode(it)) }

        if (!user.isNullOrEmpty() || !password.isNullOrEmpty()) {
            builder.append('?')
            user?.let {
                builder.append("${Property.User.getPrimaryName()}=${urlEncode(it)}")
                builder.append('&')
            }
            password?.let {
                builder.append("${Property.Password.getPrimaryName()}=${urlEncode(it)}")
            }
        }

        return Result.success(builder.toString())
    }

    override fun getAllProperties(): Array<out DatabaseConnectionUrlProperty> {
        return Property.entries.toTypedArray()
    }

    override fun getServer() = serverName

    override fun hasCredentials(): Boolean {
        if (user.isNotNullOrEmpty) return true
        if (password.isNotNullOrEmpty) return true
        return false
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var serverName
        get() = get(Property.Server)
        set(value) = set(Property.Server, value)

    var port: Int?
        get() = get(Property.Port)?.toInt()
        set(value) = set(Property.Port, value.toString())

    var database
        get() = get(Property.Database)
        set(value) = set(Property.Database, value)

    @Suppress("MemberVisibilityCanBePrivate")
    var user
        get() = get(Property.User)
        set(value) = set(Property.User, value)

    @Suppress("MemberVisibilityCanBePrivate")
    var password
        get() = get(Property.Password)
        set(value) = set(Property.Password, value)

    enum class Property(private vararg val names: String) : DatabaseConnectionUrlProperty {
        Server("Server"),
        Port("Port"),
        Database("Database"),
        User("user"),
        Password("password");

        override fun getPrimaryName(): String {
            return names[0]
        }

        override fun getNames(): Array<out String> {
            return names
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8)
    }
}