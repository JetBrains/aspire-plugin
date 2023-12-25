package me.rafaelldi.aspire.services.components

import com.intellij.ui.table.JBTable
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.generated.EnvironmentVariableModel
import javax.swing.table.DefaultTableModel

class EnvironmentVariableTable(variables: Array<EnvironmentVariableModel>) : JBTable() {
    companion object {
        private val NAME_COLUMN = AspireBundle.message("service.tab.EnvironmentVariables.Table.Name")
        private val VALUE_COLUMN = AspireBundle.message("service.tab.EnvironmentVariables.Table.Value")
    }

    private val tableModel = DefaultTableModel(arrayOf(NAME_COLUMN, VALUE_COLUMN), 0)

    init {
        variables.asSequence().sortedBy { it.key }.forEach { variable ->
            tableModel.addRow(arrayOf(variable.key, variable.value))
        }
        model = tableModel
        setShowGrid(false)
    }

    override fun isCellEditable(row: Int, column: Int): Boolean = false
}