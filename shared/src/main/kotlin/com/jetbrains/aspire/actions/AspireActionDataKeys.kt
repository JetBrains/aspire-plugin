package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireResourceData

val ASPIRE_APP_HOST_DATA: DataKey<AspireAppHostData> = DataKey.create("Aspire.Host.Data")
val ASPIRE_RESOURCE_DATA: DataKey<AspireResourceData> = DataKey.create("Aspire.Resource.Data")
