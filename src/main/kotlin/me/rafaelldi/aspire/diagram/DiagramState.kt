package me.rafaelldi.aspire.diagram

import com.intellij.diagram.v2.elements.GraphChartGroupNode
import com.intellij.diagram.v2.handles.GraphChartHandle
import com.intellij.uml.v2.elements.asLeafNode
import com.intellij.util.application
import me.rafaelldi.aspire.generated.TraceNode

class DiagramState(
    private val graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>
) {
    fun applyChanges() {
        application.runReadAction {
            graphChartHandle.asUpdateHandle().reloadDataFromGraph()
        }
    }

    fun isGroupingEnabled(): Boolean =
        graphChartHandle.asHierarchyHandle().doesGraphContainGroups()

    fun generateGroups() {
        val hierarchyHandle = graphChartHandle.asHierarchyHandle()
        val nodes = graphChartHandle.graph.nodes().groupBy { it.serviceName }

        nodes.forEach { (serviceName, nodes) ->
            hierarchyHandle.groupNodes(
                nodes.map { it.asLeafNode() }.toSet(),
                object : GraphChartGroupNode.NodesGroupProperties {
                    override fun getGroupId() = serviceName ?: ""
                    override fun getGroupTitle() = serviceName ?: ""
                },
                true
            )
        }
    }

    fun dropGroups() {
        graphChartHandle.asHierarchyHandle().ungroupAllNodes()
    }
}