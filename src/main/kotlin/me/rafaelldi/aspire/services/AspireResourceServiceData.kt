package me.rafaelldi.aspire.services

import kotlinx.coroutines.flow.SharedFlow
import me.rafaelldi.aspire.generated.ResourceModel

data class AspireResourceServiceData(
    var resourceModel: ResourceModel,
    val logFlow: SharedFlow<Unit>
)
