package com.jetbrains.rider.aspire.dashboard.components

import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension

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
