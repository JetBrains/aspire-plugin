package me.rafaelldi.aspire.services.components

import com.intellij.ui.table.JBTable
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.otel.OtelMetric
import javax.swing.table.DefaultTableModel

class MetricTable : JBTable() {
    companion object {
        private val NAME_COLUMN = AspireBundle.getMessage("service.tab.Metrics.Table.Name")
        private val VALUE_COLUMN = AspireBundle.getMessage("service.tab.Metrics.Table.Value")
    }

    private val metricRowMap: MutableMap<Pair<String, String>, Int> = mutableMapOf()
    private val tableModel = DefaultTableModel(arrayOf(NAME_COLUMN, VALUE_COLUMN), 0)

    init {
        model = tableModel
        setShowGrid(false)
    }

    override fun isCellEditable(row: Int, column: Int): Boolean = false

    fun addOrUpdate(metrics: Map<Pair<String, String>, OtelMetric>) {
        for (metric in metrics) {
            val row = metricRowMap[metric.key]
            if (row != null) {
                tableModel.setValueAt(metric.value.value, row, 1)
            } else {
                val newRow = arrayOf(metric.value.name, metric.value.value)
                tableModel.addRow(newRow)
                metricRowMap[metric.key] = (metricRowMap.values.maxOrNull() ?: -1) + 1
            }
        }
    }
}