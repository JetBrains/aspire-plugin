@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.platform.util.coroutines.flow.zipWithNext
import com.jetbrains.aspire.worker.AspireAppHost.AspireAppHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Observes [appHostState] and invokes the matching callback on each relevant lifecycle
 * transition, launched in this [CoroutineScope]. Runs until the scope is cancelled.
 *
 * The callbacks receive the launched [CoroutineScope] as their receiver so they may launch
 * scope-bound work (for example the dashboard gRPC client) directly.
 *
 * Only one collector coroutine is started, so callbacks are invoked sequentially and never
 * concurrently — state captured between them (such as a pending job reference) needs no
 * additional synchronization.
 */
internal fun CoroutineScope.launchOnAppHostTransitions(
    appHostState: StateFlow<AspireAppHostState>,
    onStarting: suspend CoroutineScope.(AspireAppHostState.Starting) -> Unit = {},
    onStarted: suspend CoroutineScope.(AspireAppHostState.Started) -> Unit = {},
    onStoppedAfterStart: suspend CoroutineScope.(previous: AspireAppHostState.Started) -> Unit = {},
): Job = launch {
    val scope = this
    appHostState
        .zipWithNext()
        .collect { (previous, current) ->
            when {
                current is AspireAppHostState.Starting ->
                    scope.onStarting(current)
                current is AspireAppHostState.Started ->
                    scope.onStarted(current)
                previous is AspireAppHostState.Started && current is AspireAppHostState.Stopped ->
                    scope.onStoppedAfterStart(previous)
            }
        }
}
