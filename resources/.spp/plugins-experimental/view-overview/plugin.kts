import com.intellij.openapi.project.Project
import spp.command.*
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.plugin.*

/**
 * Opens the 'Endpoint-Overview' dashboard via portal popup.
 */
class ViewOverviewCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_overview")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("overview") + " ➛ " + message("scope") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("class") +
            "</span></html>"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.guideMark?.getUserData(EndpointDetector.ENDPOINT_ID) ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Overview"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand(ViewOverviewCommand(project))
