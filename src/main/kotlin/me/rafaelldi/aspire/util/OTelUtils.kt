package me.rafaelldi.aspire.util

import me.rafaelldi.aspire.generated.ResourceModel
import me.rafaelldi.aspire.generated.SessionModel

private const val OTEL_RESOURCE_ATTRIBUTES = "OTEL_RESOURCE_ATTRIBUTES"
private const val SERVICE_INSTANCE_ID = "service.instance.id"

fun SessionModel.getServiceInstanceId(): String? {
    val resourceAttributes = envs?.firstOrNull { it.key.equals(OTEL_RESOURCE_ATTRIBUTES, true) }?.value
        ?: return null
    val serviceInstanceId = resourceAttributes.split(",").firstOrNull { it.startsWith(SERVICE_INSTANCE_ID) }
        ?: return null

    return serviceInstanceId.removePrefix("service.instance.id=")
}

fun ResourceModel.getServiceInstanceId(): String? {
    val resourceAttributes = environment.firstOrNull { it.key.equals(OTEL_RESOURCE_ATTRIBUTES, true) }?.value
        ?: return null
    val serviceInstanceId = resourceAttributes.split(",").firstOrNull { it.startsWith(SERVICE_INSTANCE_ID) }
        ?: return null

    return serviceInstanceId.removePrefix("service.instance.id=")
}