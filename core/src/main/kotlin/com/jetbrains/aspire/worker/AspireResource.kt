package com.jetbrains.aspire.worker

import com.intellij.openapi.diagnostic.logger
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.generated.ResourceWrapper
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.launch

class AspireResource(
    val resourceId: String,
    private val modelWrapper: ResourceWrapper,
    private val lifetime: Lifetime
) {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    init {
        val model = modelWrapper.model.valueOrNull

        lifetime.coroutineScope.launch {

        }
    }
}