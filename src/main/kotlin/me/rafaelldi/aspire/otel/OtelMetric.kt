package me.rafaelldi.aspire.otel

data class OtelMetric(
    val scope: String,
    val name: String,
    val description: String?,
    val unit: String?,
    val value: Double,
    val timestamp: Long
)
