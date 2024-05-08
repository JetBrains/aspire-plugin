package me.rafaelldi.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import me.rafaelldi.aspire.generated.ResourceState
import me.rafaelldi.aspire.generated.ResourceType

val ASPIRE_HOST_PATH: DataKey<String> = DataKey.create("Aspire.Host.Path")
val ASPIRE_RESOURCE_UID: DataKey<String> = DataKey.create("Aspire.Resource.Uid")
val ASPIRE_RESOURCE_TYPE: DataKey<ResourceType> = DataKey.create("Aspire.Resource.Type")
val ASPIRE_RESOURCE_STATE: DataKey<ResourceState?> = DataKey.create("Aspire.Resource.State")