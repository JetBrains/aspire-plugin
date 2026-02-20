package com.jetbrains.aspire.sessions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface StartSessionRequestHandler {
    companion object {
        private val EP_NAME =
            ExtensionPointName<StartSessionRequestHandler>("com.jetbrains.aspire.startSessionRequestHandler")

        fun getSupportedSessionTypes(): List<String> = EP_NAME.extensionList.map { it.sessionType }

        /**
         * Finds an applicable handler for the provided list of session start requests.
         *
         * @return An instance of `StartSessionRequestHandler` that is applicable to handle the first
         *         request in the list, or `null` if no applicable handler is available or the list is empty.
         */
        fun findApplicableHandler(requests: List<StartSessionRequest>): StartSessionRequestHandler? {
            val firstRequest = requests.firstOrNull() ?: return null
            return EP_NAME.extensionList.firstOrNull { it.isApplicable(firstRequest) }
        }
    }

    val sessionType: String

    fun isApplicable(request: StartSessionRequest): Boolean

    suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project)
}