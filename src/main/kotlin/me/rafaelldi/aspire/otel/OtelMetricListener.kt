package me.rafaelldi.aspire.otel

import com.intellij.util.messages.Topic

interface OtelMetricListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("OpenTelemetry Metric Listener", OtelMetricListener::class.java)
    }

    fun onMetricsUpdated(metrics: Map<String, MutableMap<Pair<String, String>, OtelMetric>>)
}