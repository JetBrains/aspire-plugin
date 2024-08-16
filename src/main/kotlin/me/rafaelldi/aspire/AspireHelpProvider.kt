package me.rafaelldi.aspire

import com.intellij.openapi.help.WebHelpProvider

class AspireHelpProvider : WebHelpProvider() {
    companion object {
        const val HELP_ID_PREFIX = "me.rafaelldi.aspire."
        private const val HELP_DOCS = "https://www.jetbrains.com/help/rider"
        private const val MAIN = "NET_Aspire.html"
        private const val RUN_CONFIGURATION = "NET_Aspire_run_configuration.html"
    }
    override fun getHelpPageUrl(helpTopicId: String): String? {
        if (!helpTopicId.startsWith(HELP_ID_PREFIX)) {
            return null
        }
        return when (helpTopicId.removePrefix(HELP_ID_PREFIX)) {
            "main" -> "${HELP_DOCS}/${MAIN}"
            "run-config" -> "${HELP_DOCS}/${RUN_CONFIGURATION}"
            else -> "${HELP_DOCS}/${MAIN}"
        }
    }
}