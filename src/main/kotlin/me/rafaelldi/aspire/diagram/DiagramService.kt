@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.diagram

import com.intellij.diagram.v2.GraphChartFactory
import com.intellij.diagram.v2.dsl.GraphChartEdgeStyleKtBuilderFactory
import com.intellij.diagram.v2.dsl.GraphChartKtConfigurator
import com.intellij.diagram.v2.layout.GraphChartLayoutOrientation
import com.intellij.diagram.v2.layout.GraphChartLayoutService
import com.intellij.diagram.v2.painting.GraphChartEdgePainter
import com.intellij.diagram.v2.painting.GraphChartPainterService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.uml.v2.painting.edges.GraphChartEdgePainterEdgeLabelImpl
import com.intellij.util.graph.GraphFactory
import me.rafaelldi.aspire.generated.TraceNode
import java.awt.Color

@Service(Service.Level.PROJECT)
class DiagramService(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<DiagramService>()
    }

    private val diagramStates = mutableMapOf<String, DiagramState>()

    fun showDiagramAndStoreState(fqn: String, nodes: Collection<TraceNode>, onShowed: (DiagramState) -> Unit = {}) {
        val edges = generateEdges(nodes)

        val graph = GraphFactory.getInstance()
            .directedNetwork()
            .allowsSelfLoops(true)
            .allowsParallelEdges(true)
            .build<TraceNode, TraceEdge>()

        nodes.forEach { graph.addNode(it) }
        edges.forEach { graph.addEdge(it.from, it.to, it) }

        GraphChartFactory.getInstance().apply {
            instantiateAndShowInEditor(graphChart(project, graph) {
                chartTitle = fqn
                initViewSettings()
                initPainters()
//                initSelectionListeners()
//                initToolbarActions(fqn)
            }).thenAccept {
                val diagramState = DiagramState(fqn, it.first)
                diagramState.generateGroups()
                diagramState.applyChanges()

                diagramStates[fqn] = diagramState
                onShowed(diagramState)
            }
        }
    }

    fun generateEdges(nodes: Collection<TraceNode>): List<TraceEdge> {
        val edges = mutableListOf<TraceEdge>()
        val nodeMap = nodes.associateBy { it.id }

        for (node in nodes) {
            for (connection in node.children) {
                edges.add(TraceEdge(node, nodeMap[connection.id]!!, connection.connectionCount))
            }
        }

        return edges
    }

    fun getDiagramState(fqn: String): DiagramState? = diagramStates[fqn]

    private fun GraphChartKtConfigurator<TraceNode, TraceEdge>.initViewSettings() {
        initialViewSettings {
            mergeEdgeBySources = false
            mergeEdgeByTargets = false
            currentLayouter = GraphChartLayoutService.getInstance().hierarchicLayouter
            currentLayoutOrientation = GraphChartLayoutOrientation.BOTTOM_TO_TOP
        }
    }

    private fun GraphChartKtConfigurator<TraceNode, TraceEdge>.initPainters() {
        nodePainter {
            labelWithIconNodePainter { chart, node ->
                val backgroundColor = if (chart.graph.edges().none { it.to == node }) {
                    JBColor(Color(194, 214, 252), Color(53, 116, 240))
                } else if (node.children.isEmpty()) {
                    JBColor(Color(237, 153, 161), Color(122, 67, 67))
                } else {
                    null
                }

                GraphChartPainterService.LabelWithIconNodeStyleProvider.LabelWithIcon(
                    null,
                    node.displayName,
                    backgroundColor
                )
            }
        }

        edgePainter {
            defaultEdgePainter { _, edge ->
                GraphChartEdgeStyleKtBuilderFactory.getInstance().edgeStyle {
                    targetArrow = arrow(GraphChartEdgePainter.EdgeArrowType.STANDARD)
                    lineWidth = edge.weight.weightToWidth()
                    bottomCenterLabel = GraphChartEdgePainterEdgeLabelImpl(edge.weight.toString())
                }
            }
        }
    }

//    private fun GraphChartKtConfigurator<TraceNode, TraceEdge>.initSelectionListeners() {
//        actionsForNodeRightClick.add(PopupAction())
//    }
//
//    private fun GraphChartKtConfigurator<TraceNode, TraceEdge>.initToolbarActions(fqn: String) {
//        toolbarActions.add(ShowHideGroupsAction(fqn))
//    }
}