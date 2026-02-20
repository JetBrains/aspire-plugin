package com.jetbrains.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.dashboard.AspireResourceViewModel

val ASPIRE_APP_HOST: DataKey<AspireAppHostViewModel> = DataKey.create("Aspire.Host")
val ASPIRE_RESOURCE: DataKey<AspireResourceViewModel> = DataKey.create("Aspire.Resource")