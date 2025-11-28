@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.graph

import com.intellij.diagram.v2.GraphChartFactory
import com.intellij.diagram.v2.dsl.GraphChartEdgeStyleKtBuilderFactory
import com.intellij.diagram.v2.dsl.GraphChartKtConfigurator
import com.intellij.diagram.v2.layout.GraphChartLayoutOrientation
import com.intellij.diagram.v2.layout.GraphChartLayoutService
import com.intellij.diagram.v2.painting.GraphChartEdgePainter.EdgeArrowType
import com.intellij.diagram.v2.painting.GraphChartPainterService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.graph.GraphFactory
import com.jetbrains.aspire.AspireBundle
import com.jetbrains.aspire.dashboard.AspireHost
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.util.getBaseIcon

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

    fun showResourceGraph(aspireHost: AspireHost) {
        val resources = aspireHost.getResources()

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

    private fun createResourceGraphNode(resource: AspireResource) = ResourceGraphNode(
        resource.uid,
        resource.displayName,
        getBaseIcon(
            resource.type,
            resource.containerImage?.value
        )
    )

    private fun calculateResourceNodeEdges(
        resources: List<AspireResource>,
        resourceNodes: Map<String, ResourceGraphNode>
    ): List<ResourceGraphEdge> {
        return buildList {
            for (resource in resources) {
                val sourceNode = resourceNodes[resource.displayName] ?: continue

                val relationships = resource.relationships
                    .filter { it.resourceName != resource.displayName }
                    .groupBy { it.resourceName }
                for (relationship in relationships) {
                    val targetNode = resourceNodes[relationship.key] ?: continue

                    add(ResourceGraphEdge(sourceNode, targetNode))
                }
            }
        }
    }

    private fun GraphChartKtConfigurator<ResourceGraphNode, ResourceGraphEdge>.resourceGraphConfigurator() {
        chartTitle = AspireBundle.message("resource.graph.title")

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