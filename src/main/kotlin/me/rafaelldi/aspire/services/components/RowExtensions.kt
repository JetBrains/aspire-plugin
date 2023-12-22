package me.rafaelldi.aspire.services.components

import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.InlineIconButton
import java.awt.event.ActionListener
import javax.swing.Icon

fun Row.copyableLabel(text: String) = cell(JBLabel(text).apply { setCopyable(true) })

fun Row.inlineIconButton(
    icon: Icon,
    hoveredIcon: Icon,
    value: String,
    action: (String) -> Unit
): Cell<InlineIconButton> {
    val button = InlineIconButton(icon, hoveredIcon).apply {
        actionListener = ActionListener {
            action(value)
        }
    }
    return cell(button)
}