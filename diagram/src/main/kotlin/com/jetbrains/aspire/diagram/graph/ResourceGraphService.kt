@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.diagram.graph

import com.intellij.diagram.v2.GraphChartFactory
import com.intellij.diagram.v2.dsl.GraphChartEdgeStyleKtBuilderFactory
import com.intellij.diagram.v2.dsl.GraphChartKtConfigurator
import com.intellij.diagram.v2.layout.GraphChartLayoutOrientation
import com.intellij.diagram.v2.layout.GraphChartLayoutService
import com.intellij.diagram.v2.painting.GraphChartEdgePainter.EdgeArrowType
import com.intellij.diagram.v2.painting.GraphChartPainterService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.graph.GraphFactory
import com.jetbrains.aspire.diagram.AspireDiagramBundle
import com.jetbrains.aspire.util.getResourceIcon
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireAppHostResourcesProvider
import com.jetbrains.aspire.worker.AspireResourceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Service for building a resource graph.
 *
 * This service constructs and displays a graph representation of resources
 * and their relationships.
 */
@Service(Service.Level.PROJECT)
internal class ResourceGraphService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): ResourceGraphService = project.service()
    }

    suspend fun showResourceGraph(appHostId: AspireAppHostId) {
        val resources = project.service<AspireAppHostResourcesProvider>().getResources(appHostId)

        withContext(Dispatchers.EDT) {
            showResourceGraph(resources)
        }
    }

    private fun showResourceGraph(resources: List<AspireResourceData>) {
        val resourceNodes = resources.associate { it.displayName to createResourceGraphNode(it) }
        val resourceNodeEdges = calculateResourceNodeEdges(resources, resourceNodes)

        val graph = GraphFactory.getInstance()
            .directedNetwork()
            .allowsParallelEdges(true)
            .build<ResourceGraphNode, ResourceGraphEdge>()
            .apply {
                resourceNodes.forEach { addNode(it.value) }
                resourceNodeEdges.forEach { addEdge(it.source, it.target, it) }
            }

        GraphChartFactory.getInstance().apply {
            val configuration = graphChart(project, graph) {
                resourceGraphConfigurator()
            }

            instantiateAndShowInEditor(configuration, true) {
            }
        }
    }

    private fun createResourceGraphNode(resource: AspireResourceData) = ResourceGraphNode(
        resource.uid,
        resource.displayName,
        getResourceIcon(resource.type, resource.containerImage?.value)
    )

    private fun GraphChartKtConfigurator<ResourceGraphNode, ResourceGraphEdge>.resourceGraphConfigurator() {
        chartTitle = AspireDiagramBundle.message("resource.graph.title")

        initialViewSettings {
            mergeEdgeBySources = false
            mergeEdgeByTargets = false
            currentLayouter = GraphChartLayoutService.getInstance().hierarchicLayouter
            currentLayoutOrientation = GraphChartLayoutOrientation.LEFT_TO_RIGHT
        }

        nodePainter {
            labelWithIconNodePainter { _, node ->
                GraphChartPainterService.LabelWithIconNodeStyleProvider.LabelWithIcon(node.icon, node.displayName, null)
            }
        }

        edgePainter {
            defaultEdgePainter { _, _ ->
                GraphChartEdgeStyleKtBuilderFactory.getInstance().edgeStyle {
                    targetArrow = arrow(EdgeArrowType.STANDARD)
                }
            }
        }
    }
}

@ApiStatus.Internal
fun calculateResourceNodeEdges(
    resources: List<AspireResourceData>,
    resourceNodes: Map<String, ResourceGraphNode>
): List<ResourceGraphEdge> {
    val nodePairs = mutableSetOf<Pair<ResourceGraphNode, ResourceGraphNode>>()

    return buildList {
        for (resource in resources) {
            val sourceNode = resourceNodes[resource.displayName] ?: continue

            for (relationship in resource.relationships) {
                if (relationship.resourceName == resource.displayName) continue

                val targetNode = resourceNodes[relationship.resourceName] ?: continue
                if (!nodePairs.add(sourceNode to targetNode)) continue

                add(ResourceGraphEdge(sourceNode, targetNode))
            }
        }
    }
}
