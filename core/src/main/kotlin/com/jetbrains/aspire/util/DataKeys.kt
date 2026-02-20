package com.jetbrains.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.worker.AspireResource

val ASPIRE_APP_HOST: DataKey<AspireAppHostViewModel> = DataKey.create("Aspire.Host")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")