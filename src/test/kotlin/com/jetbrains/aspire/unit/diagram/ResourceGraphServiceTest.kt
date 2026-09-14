package com.jetbrains.aspire.unit.diagram

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.diagram.graph.ResourceGraphEdge
import com.jetbrains.aspire.diagram.graph.ResourceGraphNode
import com.jetbrains.aspire.diagram.graph.calculateResourceNodeEdges
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.ResourceRelationship
import com.jetbrains.aspire.worker.ResourceType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceGraphServiceTest {
    @Test
    fun `self relationship does not create an edge`() {
        val relationship = ResourceRelationship("source", "reference")
        val relationships = listOf(relationship)
        val sourceResource = createResource("source", relationships)
        val resources = listOf(sourceResource)
        val sourceNode = ResourceGraphNode("source-uid", "source", AllIcons.FileTypes.Unknown)
        val resourceNodes = mapOf(sourceResource.displayName to sourceNode)

        val edges = calculateResourceNodeEdges(resources, resourceNodes)

        assertTrue(edges.isEmpty())
    }

    @Test
    fun `relationship to missing target does not create an edge`() {
        val relationship = ResourceRelationship("missing", "reference")
        val relationships = listOf(relationship)
        val sourceResource = createResource("source", relationships)
        val resources = listOf(sourceResource)
        val sourceNode = ResourceGraphNode("source-uid", "source", AllIcons.FileTypes.Unknown)
        val resourceNodes = mapOf(sourceResource.displayName to sourceNode)

        val edges = calculateResourceNodeEdges(resources, resourceNodes)

        assertTrue(edges.isEmpty())
    }

    @Test
    fun `multiple relationships to the same target create one edge`() {
        val firstRelationship = ResourceRelationship("target", "reference")
        val secondRelationship = ResourceRelationship("target", "parent")
        val sourceRelationships = listOf(firstRelationship, secondRelationship)
        val sourceResource = createResource("source", sourceRelationships)
        val targetRelationships = emptyList<ResourceRelationship>()
        val targetResource = createResource("target", targetRelationships)
        val resources = listOf(sourceResource, targetResource)
        val sourceNode = ResourceGraphNode("source-uid", "source", AllIcons.FileTypes.Unknown)
        val targetNode = ResourceGraphNode("target-uid", "target", AllIcons.FileTypes.Unknown)
        val resourceNodes = mapOf(
            sourceResource.displayName to sourceNode,
            targetResource.displayName to targetNode,
        )
        val expectedEdge = ResourceGraphEdge(sourceNode, targetNode)
        val expectedEdges = listOf(expectedEdge)

        val edges = calculateResourceNodeEdges(resources, resourceNodes)

        assertEquals(expectedEdges, edges)
    }

    private fun createResource(
        displayName: String,
        relationships: List<ResourceRelationship>,
    ): AspireResourceData {
        val appHostId = AspireAppHostId("app-host")
        val resourceId = AspireResourceId(appHostId, displayName)

        return AspireResourceData(
            id = resourceId,
            uid = "$displayName-uid",
            name = displayName,
            type = ResourceType.Unknown,
            originType = "Unknown",
            displayName = displayName,
            state = null,
            stateStyle = null,
            isHidden = false,
            healthStatus = null,
            urls = emptyList(),
            environment = emptyList(),
            volumes = emptyList(),
            relationships = relationships,
            parentDisplayName = null,
            commands = emptyList(),
            createdAt = null,
            startedAt = null,
            stoppedAt = null,
            exitCode = null,
            pid = null,
            projectPath = null,
            executablePath = null,
            executableWorkDir = null,
            args = null,
            containerImage = null,
            containerId = null,
            containerPorts = null,
            containerCommand = null,
            containerArgs = null,
            containerLifetime = null,
            connectionString = null,
            source = null,
            value = null,
        )
    }
}
