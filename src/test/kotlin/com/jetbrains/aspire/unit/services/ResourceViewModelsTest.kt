package com.jetbrains.aspire.unit.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.services.toResourceViewModels
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import com.jetbrains.aspire.worker.AspireResourceModel
import com.jetbrains.aspire.worker.ResourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceViewModelsTest {
    private lateinit var testRootDisposable: Disposable

    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @BeforeEach
    fun setUpSettings() {
        testRootDisposable = Disposer.newDisposable("ResourceViewModelsTest")
        ApplicationManager.getApplication().replaceService(AspireSettings::class.java, AspireSettings(), testRootDisposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(testRootDisposable)
    }

    @Test
    fun `resource view model is created and added to the list`() = runTest {
        val project = ProjectManager.getInstance().defaultProject
        val parent = Disposer.newDisposable("parent")
        Disposer.register(testRootDisposable, parent)
        val resource = TestResource("resource")
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(resource))

        val viewModels = resources.toResourceViewModels(project, backgroundScope, parent)

        runCurrent()
        val viewModel = viewModels.value.single()
        assertSame(resource, viewModel.resource)
    }

    @Test
    fun `resource view models are sorted and reused when resources change`() = runTest {
        val project = ProjectManager.getInstance().defaultProject
        val parent = Disposer.newDisposable("parent")
        Disposer.register(testRootDisposable, parent)
        val container = TestResource("container", ResourceType.Container)
        val projectResource = TestResource("project", ResourceType.Project)
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(container, projectResource))

        val viewModels = resources.toResourceViewModels(project, backgroundScope, parent)

        runCurrent()
        val first = viewModels.value
        assertEquals(listOf("project", "container"), first.map { it.resourceName })

        resources.value = listOf(projectResource, container)
        runCurrent()
        val second = viewModels.value
        assertSame(first[0], second[0])
        assertSame(first[1], second[1])
    }

    @Test
    fun `removed view models are disposed and new ones belong to the parent`() = runTest {
        val project = ProjectManager.getInstance().defaultProject
        val parent = Disposer.newDisposable("parent")
        Disposer.register(testRootDisposable, parent)
        val oldResource = TestResource("old")
        val newResource = TestResource("new")
        val resources = MutableStateFlow<List<AspireResourceModel>>(listOf(oldResource))

        val viewModels = resources.toResourceViewModels(project, backgroundScope, parent)
        runCurrent()
        val oldViewModel = viewModels.value.single()

        resources.value = listOf(newResource)
        runCurrent()
        val newViewModel = viewModels.value.single()

        assertEquals("new", newViewModel.resourceName)
        assertFalse(Disposer.tryRegister(oldViewModel) {})
    }

    private class TestResource(
        override val resourceName: String,
        type: ResourceType = ResourceType.Unknown,
    ) : AspireResourceModel {
        override val data = MutableStateFlow(
            AspireResourceData(
                id = AspireResourceId(AspireAppHostId("app-host"), resourceName),
                uid = "uid-$resourceName",
                name = resourceName,
                type = type,
                originType = "Project",
                displayName = resourceName,
                state = null,
                stateStyle = null,
                isHidden = false,
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
        )
        override val childrenResources = MutableStateFlow<List<AspireResourceModel>>(emptyList())
        override val logFlow = MutableSharedFlow<AspireResourceLogEntry>()
    }
}