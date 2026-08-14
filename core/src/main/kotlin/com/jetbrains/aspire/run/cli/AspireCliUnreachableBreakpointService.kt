package com.jetbrains.aspire.run.cli

import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDocument
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.extensions.AppHostEntryPoint
import com.jetbrains.aspire.extensions.AppHostEntryPointLocator
import kotlinx.coroutines.*
import javax.swing.Icon
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * Marks the breakpoints an [AspireCliRunConfiguration] can never hit with the invalid-breakpoint icon and an
 * explanatory tooltip.
 *
 * `aspire run` owns the AppHost process, so the IDE cannot launch it and instead attaches while Aspire spins
 * inside `DistributedApplication.CreateBuilder` (see [com.jetbrains.aspire.extensions.AppHostDebuggerExtension]).
 * Everything at or above that line has already executed by then, so breakpoints there silently never hit.
 * There is no way to make them hit, so the next best thing is to say so in the gutter as soon as an Aspire (CLI)
 * configuration becomes the selected one.
 *
 * The marker is written to the *global* breakpoint presentation via
 * [XBreakpointManager.updateBreakpointPresentation] rather than to a debug session's presentation. Rider's own
 * verified-breakpoint updates go to the session slot, so neither side clobbers the other: our marker outlives a
 * debug session instead of being reset by breakpoint binding. Note that while a session is running,
 * `XBreakpointUIUtil.calculateIcon` prefers the session icon, so Rider's bound-breakpoint icon is shown in
 * place of ours for that time; the message stays ours, because `XBreakpointBase.getErrorMessage()` only prefers
 * a *non-null* session message.
 *
 * Getting the message into the gutter tooltip takes one extra step beyond writing the presentation — see
 * [updatePresentation].
 */
@Service(Service.Level.PROJECT)
internal class AspireCliUnreachableBreakpointService(private val project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): AspireCliUnreachableBreakpointService = project.service()

        private val LOG = logger<AspireCliUnreachableBreakpointService>()

        // Collapses bursts of breakpoint and document events into a single backend round-trip.
        private val REFRESH_DEBOUNCE = 300.milliseconds
    }

    private val lock = Any()
    private var refreshJob: Job? = null
    private var documentListenerDisposable: Disposable? = null
    private val markedBreakpoints = mutableSetOf<XLineBreakpoint<*>>()

    /**
     * Re-resolves the boundary line and re-applies the markers after a short debounce, replacing any refresh
     * that has not run yet.
     */
    fun scheduleRefresh() {
        synchronized(lock) {
            refreshJob?.cancel()
            refreshJob = AspireService.getInstance(project).scope.launch {
                delay(REFRESH_DEBOUNCE)
                refresh()
            }
        }
    }

    /**
     * Drops [breakpoint] from the bookkeeping without touching its presentation, for breakpoints that are
     * being removed anyway.
     */
    fun forget(breakpoint: XBreakpoint<*>) {
        if (breakpoint !is XLineBreakpoint<*>) return
        synchronized(lock) { markedBreakpoints.remove(breakpoint) }
    }

    private suspend fun refresh() {
        val entryPoint = resolveEntryPoint()
        if (entryPoint == null) {
            withContext(Dispatchers.EDT) {
                clearMarkers()
                detachDocumentListener()
            }
            return
        }

        val entryFile = VirtualFileManager.getInstance().findFileByNioPath(entryPoint.filePath)
        if (entryFile == null) {
            LOG.warn("Unable to find the AppHost entry file ${entryPoint.filePath} in the virtual file system")
            withContext(Dispatchers.EDT) {
                clearMarkers()
                detachDocumentListener()
            }
            return
        }

        LOG.trace {
            "DistributedApplication.CreateBuilder is on line ${entryPoint.createBuilderLine} of ${entryFile.url}"
        }

        withContext(Dispatchers.EDT) {
            applyMarkers(entryFile.url, entryPoint.createBuilderLine)
            // Editing the AppHost above `CreateBuilder` moves the boundary, so follow the entry file.
            attachDocumentListener(entryFile)
        }
    }

    /**
     * Resolves the `CreateBuilder` location of the AppHost of the currently selected run configuration, or
     * `null` when the selected configuration is not an Aspire (CLI) one — in which case no breakpoint of this
     * project is unreachable and every marker must go.
     */
    private suspend fun resolveEntryPoint(): AppHostEntryPoint? {
        val configuration = RunManager.getInstance(project).selectedConfiguration?.configuration
        if (configuration !is AspireCliRunConfiguration) return null

        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.isNotBlank() } ?: return null
        val path = Path(appHostFilePath)

        val locator = AppHostEntryPointLocator.findApplicable(path)
        if (locator == null) {
            LOG.trace { "No AppHost entry point locator applies to $path" }
            return null
        }

        return locator.findEntryPoint(path, project)
    }

    private fun applyMarkers(entryFileUrl: String, createBuilderLine: Int) {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

        val unreachable = breakpointManager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>()
            // `<=` and not `<`: the debugger attaches while the main thread is already inside
            // `DistributedApplicationBuilder`'s constructor, so the call-site line has started executing too
            // and a breakpoint on the `CreateBuilder` line itself does not hit either.
            .filter { it.fileUrl == entryFileUrl && it.line <= createBuilderLine }
            .toSet()

        val stale = synchronized(lock) {
            val stale = markedBreakpoints - unreachable
            markedBreakpoints.clear()
            markedBreakpoints.addAll(unreachable)
            stale
        }

        for (breakpoint in stale) {
            updatePresentation(breakpointManager, breakpoint, null, null)
        }
        for (breakpoint in unreachable) {
            updatePresentation(
                breakpointManager,
                breakpoint,
                AllIcons.Debugger.Db_invalid_breakpoint,
                AspireCoreBundle.message("breakpoint.unreachable.before.create.builder")
            )
        }
    }

    private fun clearMarkers() {
        val marked = synchronized(lock) {
            if (markedBreakpoints.isEmpty()) return
            markedBreakpoints.toSet().also { markedBreakpoints.clear() }
        }

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        for (breakpoint in marked) {
            updatePresentation(breakpointManager, breakpoint, null, null)
        }
    }

    /**
     * Applies a presentation and makes the gutter *tooltip* follow it, not just the icon.
     *
     * [XBreakpointManager.updateBreakpointPresentation] refreshes only the presentation itself, which is what
     * moves the icon. The tooltip text is a separate value that the split debugger (on by default via
     * `xdebugger.toolwindow.split`, even in a local IDE) recomputes solely when the breakpoint's change flow
     * emits — so without a nudge the icon updates while the tooltip keeps its old text.
     *
     * [XBreakpointBase.emitBreakpointChanged] emits exactly that flow and nothing else. Its bigger sibling
     * `fireBreakpointChanged` would additionally notify every [XBreakpointListener], which would re-enter
     * [AspireCliBreakpointListener] and make Rider re-register the breakpoint with a running debugger, so it is
     * deliberately not used here.
     */
    private fun updatePresentation(
        breakpointManager: XBreakpointManager,
        breakpoint: XLineBreakpoint<*>,
        icon: Icon?,
        message: String?
    ) {
        val base = breakpoint as? XBreakpointBase<*, *, *>
        val previousMessage = base?.customizedPresentation?.errorMessage

        breakpointManager.updateBreakpointPresentation(breakpoint, icon, message)

        // Only on a real transition: a refresh runs on every breakpoint event and every edit of the AppHost
        // file, and re-asserts the presentation of breakpoints that are already marked. Emitting for those too
        // would recompute the tooltip of every marked breakpoint on every keystroke burst.
        if (base != null && previousMessage != message) {
            base.emitBreakpointChanged()
        }
    }

    private fun attachDocumentListener(entryFile: VirtualFile) {
        detachDocumentListener()

        val document = entryFile.findDocument() ?: return

        val disposable = Disposer.newDisposable("Aspire CLI AppHost entry file watcher")
        Disposer.register(this, disposable)
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleRefresh()
        }, disposable)

        synchronized(lock) { documentListenerDisposable = disposable }
    }

    private fun detachDocumentListener() {
        val disposable = synchronized(lock) {
            documentListenerDisposable.also { documentListenerDisposable = null }
        }
        disposable?.let { Disposer.dispose(it) }
    }

    override fun dispose() {
        synchronized(lock) {
            refreshJob?.cancel()
            refreshJob = null
            // The customized presentation is in-memory only, so there is nothing to restore once the project
            // (and with it every breakpoint of this project) is going away.
            markedBreakpoints.clear()
        }
    }
}
