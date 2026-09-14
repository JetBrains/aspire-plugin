package com.jetbrains.aspire.diagram.graph

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ResourceGraphEdge(val source: ResourceGraphNode, val target: ResourceGraphNode)
