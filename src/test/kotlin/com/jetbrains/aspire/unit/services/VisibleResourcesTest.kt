package com.jetbrains.aspire.unit.services

import com.jetbrains.aspire.services.visibleResources
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import com.jetbrains.aspire.worker.AspireResourceModel
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleResourcesTest {
    @Test
    fun `hidden resources are filtered until the setting is enabled`() = runTest {
        val visible = TestResource("visible")
        val hidden = TestResource("hidden", isHidden = true)
        val hiddenState = TestResource("hidden-state", state = "Hidden")
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(visible, hidden, hiddenState))
        val showHidden = MutableStateFlow(false)
        var result = emptyList<AspireResourceModel>()
        backgroundScope.launch { resources.visibleResources(showHidden).collect { result = it } }

        runCurrent()
        assertEquals(listOf("visible"), result.map { it.resourceName })

        showHidden.value = true
        runCurrent()
        assertEquals(listOf("visible", "hidden", "hidden-state"), result.map { it.resourceName })

        showHidden.value = false
        runCurrent()
        assertEquals(listOf("visible"), result.map { it.resourceName })
    }

    @Test
    fun `changing resource visibility updates the displayed list`() = runTest {
        val resource = TestResource("resource")
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(resource))
        var result = emptyList<AspireResourceModel>()
        backgroundScope.launch { resources.visibleResources(MutableStateFlow(false)).collect { result = it } }

        runCurrent()
        assertEquals(listOf(resource), result)

        resource.setHidden(true)
        runCurrent()
        assertEquals(emptyList(), result)

        resource.setHidden(false)
        runCurrent()
        assertEquals(listOf(resource), result)
    }

    @Test
    fun `visible children of hidden parents stay accessible`() = runTest {
        val parent = TestResource("parent", isHidden = true)
        val child = TestResource("child")
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(parent))
        val showHidden = MutableStateFlow(false)
        var result = emptyList<AspireResourceModel>()
        backgroundScope.launch { resources.visibleResources(showHidden).collect { result = it } }

        runCurrent()
        parent.childrenResources.value = listOf(child)
        runCurrent()
        assertEquals(listOf(child), result)

        showHidden.value = true
        runCurrent()
        assertEquals(listOf(parent), result)
    }

    @Test
    fun `visible descendants pass through multiple hidden ancestors`() = runTest {
        val grandparent = TestResource("grandparent", isHidden = true)
        val parent = TestResource("parent", state = "Hidden")
        val child = TestResource("child")
        parent.childrenResources.value = listOf(child)
        grandparent.childrenResources.value = listOf(parent)
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(grandparent))
        val showHidden = MutableStateFlow(false)
        var result = emptyList<AspireResourceModel>()
        backgroundScope.launch { resources.visibleResources(showHidden).collect { result = it } }

        runCurrent()
        assertEquals(listOf(child), result)
    }

    @Test
    fun `unrelated hidden resource updates do not re-emit children`() = runTest {
        val parent = TestResource("parent", isHidden = true)
        val child = TestResource("child")
        parent.childrenResources.value = listOf(child)
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(parent))
        var emissions = 0
        var result = emptyList<AspireResourceModel>()
        backgroundScope.launch {
            resources.visibleResources(MutableStateFlow(false)).collect {
                result = it
                emissions++
            }
        }

        runCurrent()
        assertEquals(listOf(child), result)
        assertEquals(1, emissions)

        parent.data.value = parent.data.value.copy(displayName = "Updated")
        runCurrent()
        assertEquals(listOf(child), result)
        assertEquals(1, emissions)
    }

    private class TestResource(
        override val resourceName: String,
        isHidden: Boolean = false,
        state: String = "Running",
    ) : AspireResourceModel {
        override val data = MutableStateFlow(createData(isHidden, state))
        override val childrenResources = MutableStateFlow<List<AspireResourceModel>>(emptyList())
        override val logFlow = MutableSharedFlow<AspireResourceLogEntry>()

        fun setHidden(value: Boolean) {
            data.value = createData(value, "Running")
        }

        private fun createData(isHidden: Boolean, state: String): AspireResourceData =
            AspireResourceData(
                id = AspireResourceId(AspireAppHostId("app-host"), resourceName),
                uid = "uid-$resourceName",
                name = resourceName,
                type = ResourceType.Unknown,
                originType = "Project",
                displayName = resourceName,
                state = if (state == "Hidden") ResourceState.Hidden else null,
                stateStyle = null,
                isHidden = isHidden,
                healthStatus = null,
                urls = emptyList(),
                environment = emptyList(),
                volumes = emptyList(),
                relationships = emptyList(),
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
