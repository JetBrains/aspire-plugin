package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
interface ConnectionStringModifier {
    companion object {
        private val EP_NAME =
            ExtensionPointName<ConnectionStringModifier>("com.jetbrains.aspire.connectionStringModifier")

        fun getInstance(): ConnectionStringModifier? = EP_NAME.extensionList.firstOrNull()
    }

    suspend fun modifyConnectionString(project: Project, context: ConnectionStringContext): Result<String>
}

@ApiStatus.Internal
data class ConnectionStringContext(
    val name: String,
    val connectionString: String,
    val urls: List<URI>,
    val containerId: String,
    val containerPorts: String?,
)
