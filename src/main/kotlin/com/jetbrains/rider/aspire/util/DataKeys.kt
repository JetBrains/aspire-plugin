package com.jetbrains.rider.aspire.util

import com.intellij.openapi.actionSystem.DataKey
import com.jetbrains.rider.aspire.services.AspireResource
import java.nio.file.Path

val ASPIRE_HOST_PATH: DataKey<Path> = DataKey.create("Aspire.Host.Path")
val ASPIRE_RESOURCE: DataKey<AspireResource> = DataKey.create("Aspire.Resource")