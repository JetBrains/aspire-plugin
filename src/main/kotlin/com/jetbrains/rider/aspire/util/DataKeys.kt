package com.jetbrains.rider.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import java.nio.file.Path

val ASPIRE_HOST_PATH: DataKey<Path> = DataKey.create("Aspire.Host.Path")
val ASPIRE_RESOURCE_UID: DataKey<String> = DataKey.create("Aspire.Resource.Uid")
val ASPIRE_RESOURCE_SERVICE_INSTANCE_ID: DataKey<String> = DataKey.create("Aspire.Resource.Service.Instance.Id")
val ASPIRE_RESOURCE_TYPE: DataKey<ResourceType> = DataKey.create("Aspire.Resource.Type")
val ASPIRE_RESOURCE_STATE: DataKey<ResourceState?> = DataKey.create("Aspire.Resource.State")