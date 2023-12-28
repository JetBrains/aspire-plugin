package me.rafaelldi.aspire.services.components

import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.generated.MetricKey
import me.rafaelldi.aspire.generated.MetricValue

class SessionMetricPanel : BorderLayoutPanel() {
    private val table = MetricTable(this)
    private var chosenMetric: MetricKey? = null
    private var chartPanel: MetricChartPanel? = null

    private val splitter = OnePixelSplitter(false).apply {
        firstComponent = ScrollPaneFactory.createScrollPane(table, SideBorder.NONE)
        secondComponent = JBPanelWithEmptyText()
            .withEmptyText(AspireBundle.message("service.tab.metrics.select.metric"))
    }

    init {
        add(splitter)
    }

    fun metricSelected(scope: String, metric: String, value: Double, unit: String) {
        chosenMetric = MetricKey(scope, metric)
        chartPanel = MetricChartPanel(metric, value, unit)
        splitter.secondComponent = chartPanel
    }

    fun updateMetrics(metrics: Map<MetricKey, MetricValue>) {
        table.addOrUpdate(metrics)
        chartPanel?.let {
            val key = chosenMetric ?: return@let
            val metric = metrics[key] ?: return@let
            it.update(metric.value, metric.timestamp)
        }
    }
}