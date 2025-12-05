package com.jetbrains.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.dashboard.AspireHost
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.dashboard.AspireResourceViewModel

val ASPIRE_HOST: DataKey<AspireHost> = DataKey.create("Aspire.Host")
val ASPIRE_APP_HOST: DataKey<AspireAppHostViewModel> = DataKey.create("Aspire.AppHost")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")
val ASPIRE_RESOURCE2: DataKey<AspireResourceViewModel> = DataKey.create("Aspire.Resource")