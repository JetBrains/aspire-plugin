package com.jetbrains.rider.aspire.graph

import javax.swing.Icon

data class ResourceGraphNode(
    val uid: String,
    val displayName: String,
    val icon: Icon
)