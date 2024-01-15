package me.rafaelldi.aspire.diagram

import me.rafaelldi.aspire.generated.TraceNode

class TraceEdge(
    val from: TraceNode,
    val to: TraceNode,
    var weight: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TraceEdge

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }
}