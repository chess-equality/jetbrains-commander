import spp.plugin.*
import spp.command.*
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG

/**
 * Opens the 'Endpoint-Traces' dashboard via portal popup.
 */
class ViewTracesCommand : LiveCommand() {
    override val name = message("view_traces")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" + message("live_view") + " ➛ " +
            message("traces") + " ➛ " + message("scope") + ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" +
            message("method") + "</span></html>"
    override val selectedIcon = "view-traces/icons/view-traces_selected.svg"
    override val unselectedIcon = "view-traces/icons/view-traces_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.getUserData(EndpointDetector.ENDPOINT_ID.name) as String? ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Traces"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand { ViewTracesCommand() }
