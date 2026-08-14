package com.jetbrains.aspire.run.cli

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener

/**
 * Keeps the unreachable-breakpoint markers of the selected [AspireCliRunConfiguration] in sync as the user
 * adds, moves and removes breakpoints.
 *
 * There is no feedback loop with [AspireCliUnreachableBreakpointService]: its
 * [com.intellij.xdebugger.breakpoints.XBreakpointManager.updateBreakpointPresentation] calls fire
 * [breakpointPresentationUpdated], not [breakpointChanged].
 */
internal class AspireCliBreakpointListener(private val project: Project) : XBreakpointListener<XBreakpoint<*>> {
    override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
        AspireCliUnreachableBreakpointService.getInstance(project).scheduleRefresh()
    }

    override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
        AspireCliUnreachableBreakpointService.getInstance(project).scheduleRefresh()
    }

    override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
        AspireCliUnreachableBreakpointService.getInstance(project).forget(breakpoint)
    }
}
