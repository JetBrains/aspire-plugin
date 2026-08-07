package com.jetbrains.aspire.worker.dcp

import com.intellij.openapi.util.registry.Registry

/**
 * Feature flag selecting the DCP transport.
 *
 * When enabled, each [com.jetbrains.aspire.worker.AspireAppHost] runs its own in-process
 * [AspireSessionServer] to talk to DCP. When disabled (the default), the plugin uses the external
 * .NET AspireWorker process instead.
 *
 * The key is declared in `intellij.aspire.core.xml`.
 */
internal object AspireEmbeddedSessionHost {
    private const val REGISTRY_KEY = "aspire.embedded.session.host"

    fun isEnabled(): Boolean = Registry.`is`(REGISTRY_KEY, false)
}
