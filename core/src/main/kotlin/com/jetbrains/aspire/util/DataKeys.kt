package com.jetbrains.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireResource

val ASPIRE_APP_HOST: DataKey<AspireAppHost> = DataKey.create("Aspire.Host")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")
