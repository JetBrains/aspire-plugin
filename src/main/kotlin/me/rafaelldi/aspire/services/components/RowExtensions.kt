package me.rafaelldi.aspire.services.components

import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.Icon

fun Row.copyableLabel(
    text: String,
    style: UIUtil.ComponentStyle = UIUtil.ComponentStyle.REGULAR,
    color: UIUtil.FontColor = UIUtil.FontColor.NORMAL
) = cell(JBLabel(text, style, color).apply { setCopyable(true) })

fun Row.separator(): Cell<SeparatorComponent> {
    val separator = object : SeparatorComponent(
        JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(),
        SeparatorOrientation.VERTICAL
    ) {
        override fun getPreferredSize() = Dimension(1, 17)
    }
    return cell(separator)
}

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