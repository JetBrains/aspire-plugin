package me.rafaelldi.aspire.actions.chart

import com.intellij.diagram.v2.actions.GraphChartNodeRightClickAction
import com.intellij.diagram.v2.handles.GraphChartHandle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.rafaelldi.aspire.diagram.TraceEdge
import me.rafaelldi.aspire.generated.TraceNode
import javax.swing.JEditorPane
import javax.swing.JLabel

class PopupAction : GraphChartNodeRightClickAction<TraceNode, TraceEdge> {
    override fun setupTemplatePresentation(
        presentation: Presentation,
        graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>
    ) {
        presentation.icon = AllIcons.General.Information
        presentation.text = "Show Additional Info"
    }

    override fun onNodeRightClick(graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>, node: TraceNode) {
        val content = getNodeAttributesHtmlContent(node)

        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(
            JEditorPane(UIUtil.HTML_MIME, content.toString()).apply {
                border = JBUI.Borders.empty(10, 16)
                isEnabled = true
                isEditable = false
                font = JLabel().font
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            },
            null
        ).createPopup()

        popup.showCenteredInCurrentWindow(graphChartHandle.project)
    }

    private fun getNodeAttributesHtmlContent(node: TraceNode): HtmlChunk {
        val builder = HtmlBuilder()

        for ((index, attribute) in node.attributes.withIndex()) {
            builder.append(HtmlChunk.text("${attribute.key}: ").bold()).append(HtmlChunk.text(attribute.value))

            if (index != node.attributes.size - 1) {
                builder.append(HtmlChunk.br())
            }
        }

        return builder.wrapWithHtmlBody()
    }
}