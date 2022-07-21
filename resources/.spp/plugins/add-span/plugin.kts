import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.status.LiveStatusManager

class AddSpanCommand : LiveCommand() {
    override val name = message("add_span")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("on_method") +
            " *methodName*</span></html>"
    override var selectedIcon = findIcon("icons/live-span_selected.svg")
    override var unselectedIcon = findIcon("icons/live-span_unselected.svg")

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            LiveStatusManager.showSpanStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }
}

if (liveInstrumentService != null) {
    registerCommand(AddSpanCommand())
}
