package me.rafaelldi.aspire.services.components

import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.otel.OtelMetric

class SessionMetricPanel : BorderLayoutPanel() {

    private val table = MetricTable()

    init {
        val splitter = OnePixelSplitter(false).apply {
            firstComponent = ScrollPaneFactory.createScrollPane(table, SideBorder.NONE)
            secondComponent = panel { }
        }
        add(splitter)
    }

    fun updateMetrics(metrics: Map<Pair<String, String>, OtelMetric>) {
        table.addOrUpdate(metrics)
    }
}