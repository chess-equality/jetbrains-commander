import com.intellij.openapi.project.Project
import liveplugin.*
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG

/**
 * Opens the 'Endpoint-Logs' dashboard via portal popup.
 */
class ViewLogsCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_logs")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("logs") + " ➛ " + message("scope") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("method") +
            "</span></html>"
    override val selectedIcon = "view-logs/icons/view-logs_selected.svg"
    override val unselectedIcon = "view-logs/icons/view-logs_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.getUserData(EndpointDetector.ENDPOINT_ID.name) as String? ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Logs"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand { ViewLogsCommand(project!!) }
