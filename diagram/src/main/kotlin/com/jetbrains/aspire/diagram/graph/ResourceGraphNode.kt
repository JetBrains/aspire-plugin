package com.jetbrains.aspire.diagram.graph

import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
data class ResourceGraphNode(
    val uid: String,
    val displayName: String,
    val icon: Icon
)
