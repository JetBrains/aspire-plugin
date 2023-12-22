package me.rafaelldi.aspire.services

import me.rafaelldi.aspire.generated.AspireSessionHostModel

data class SessionHostServiceData(
    val id: String,
    val hostName: String,
    val dashboardUrl: String?,
    val hostModel: AspireSessionHostModel
)