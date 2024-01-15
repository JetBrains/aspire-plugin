package me.rafaelldi.aspire.actions.chart

import com.intellij.diagram.v2.actions.GraphChartToolbarAction
import com.intellij.diagram.v2.handles.GraphChartHandle
import com.intellij.openapi.actionSystem.Presentation
import icons.RiderIcons
import me.rafaelldi.aspire.diagram.DiagramService
import me.rafaelldi.aspire.diagram.TraceEdge
import me.rafaelldi.aspire.generated.TraceNode

class ShowHideGroupsAction(private val title: String) :
    GraphChartToolbarAction.GraphChartToolbarToggleAction<TraceNode, TraceEdge> {

    private var diagramService: DiagramService? = null

    override fun setupTemplatePresentation(
        presentation: Presentation,
        graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>
    ) {
        diagramService = DiagramService.getInstance(graphChartHandle.project)
        presentation.icon = RiderIcons.Toolbar.Grouping
        presentation.text = if (isSelected) "Hide Groups" else "Show Groups"
    }

    override fun isSelected() =
        diagramService?.getDiagramState(title)?.isGroupingEnabled() ?: false

    override fun onToggle(graphChartHandle: GraphChartHandle<TraceNode, TraceEdge>, isEnabled: Boolean) {
        if (isEnabled) {
            diagramService?.getDiagramState(title)?.generateGroups()
            diagramService?.getDiagramState(title)?.applyChanges()
        } else {
            diagramService?.getDiagramState(title)?.dropGroups()
            diagramService?.getDiagramState(title)?.applyChanges()
        }
    }
}