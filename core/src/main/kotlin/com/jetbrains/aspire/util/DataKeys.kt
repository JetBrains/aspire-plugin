package com.jetbrains.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.dashboard.AspireHost
import com.jetbrains.aspire.dashboard.AspireResource

val ASPIRE_HOST: DataKey<AspireHost> = DataKey.create("Aspire.Host")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")