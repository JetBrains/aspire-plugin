package com.jetbrains.rider.aspire.graph

import javax.swing.Icon

internal data class ResourceGraphNode(
    val uid: String,
    val displayName: String,
    val icon: Icon
)