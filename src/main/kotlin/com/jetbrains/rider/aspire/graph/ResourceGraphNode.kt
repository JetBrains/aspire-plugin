package com.jetbrains.rider.aspire.graph

import com.jetbrains.rider.aspire.generated.ResourceType

data class ResourceGraphNode(val uid: String, val displayName: String, val type: ResourceType)