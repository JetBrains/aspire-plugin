package com.jetbrains.rider.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.rider.aspire.services.AspireHost
import com.jetbrains.rider.aspire.services.AspireResource

val ASPIRE_HOST: DataKey<AspireHost> = DataKey.create("Aspire.Host")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")