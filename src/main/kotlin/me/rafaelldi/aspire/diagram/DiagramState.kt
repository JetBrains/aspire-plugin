package me.rafaelldi.aspire.diagram

import com.intellij.diagram.v2.handles.GraphChartHandle
import com.intellij.uml.v2.elements.GraphChartLeafNodeWrapper
import me.rafaelldi.aspire.generated.TraceNode

class DiagramState(val fqn: String, private val graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>) {
    fun applyChanges() {
        graphChartHandle.asUpdateHandle().reloadDataFromGraph()
    }

    fun addNode(node: TraceNode) {
        graphChartHandle.graph.nodes().add(node)
    }

    fun addEdge(edge: TraceEdge) {
        graphChartHandle.graph.edges().add(edge)
    }

    fun isGroupingEnabled(): Boolean {
        return graphChartHandle.asHierarchyHandle().doesGraphContainGroups()
    }

    fun generateGroups() {
        val hierarchyHandle = graphChartHandle.asHierarchyHandle()
        val nodes = graphChartHandle.graph.nodes().groupBy { it.serviceName }

        nodes.forEach { (serviceName, nodes) ->
            hierarchyHandle.groupNodes(
                nodes.map { GraphChartLeafNodeWrapper.of(it) },
                { serviceName ?: "" },
                true
            )
        }
    }

    fun dropGroups() {
        graphChartHandle.asHierarchyHandle().ungroupAllNodes()
    }
}