package me.rafaelldi.aspire.sessionHost

import com.intellij.util.messages.Topic

interface AspireSessionListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire Session Listener", AspireSessionListener::class.java)
    }

    fun sessionStarted(otelServiceName: String)
    fun sessionTerminated(otelServiceName: String)
}