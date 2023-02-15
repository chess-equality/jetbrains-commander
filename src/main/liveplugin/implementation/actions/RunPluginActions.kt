package liveplugin.implementation.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Icons.rerunPluginIcon
import liveplugin.implementation.common.Icons.runPluginIcon
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.canBeHandledBy
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPlugins
import liveplugin.implementation.pluginrunner.pluginRunners

class RunPluginAction : AnAction("Load Plugin", "Load live plugin", runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        runPlugins(event.livePlugins(), event)
    }

    override fun update(event: AnActionEvent) {
        val livePlugins = event.livePlugins()
        event.presentation.isEnabled = livePlugins.canBeHandledBy(pluginRunners)
        if (event.presentation.isEnabled) {
            val hasPluginsToUnload = livePlugins.any { it.canBeUnloaded(event.project) }
            val actionName = if (hasPluginsToUnload) "Re-load" else "Load"
            event.presentation.setText("$actionName ${pluginNameInActionText(livePlugins)}", false)
            event.presentation.icon = if (hasPluginsToUnload) rerunPluginIcon else runPluginIcon
        }
    }

    override fun getActionUpdateThread() = BGT

    companion object {
        fun pluginNameInActionText(livePlugins: List<LivePlugin>): String =
            when (livePlugins.size) {
                0    -> "Plugin"
                1    -> "'${livePlugins.first().id}' Plugin"
                else -> "Selected Plugins"
            }
    }
}

class RunLivePluginsGroup : DefaultActionGroup(
    RunPluginAction().hiddenWhenDisabled(),
    UnloadPluginAction().hiddenWhenDisabled(),
    Separator()
) {
    init {
        isPopup = false
    }

    companion object {
        fun AnAction.hiddenWhenDisabled(): AnAction = HiddenWhenDisabledAction(this)

        private class HiddenWhenDisabledAction(private val delegate: AnAction) : AnAction(), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = delegate.actionPerformed(event)

            override fun update(event: AnActionEvent) {
                val presentation = delegate.templatePresentation
                event.presentation.text = presentation.text
                event.presentation.description = presentation.description
                event.presentation.icon = presentation.icon

                delegate.update(event)
                if (!event.presentation.isEnabled) event.presentation.isVisible = false
            }

            override fun getActionUpdateThread() = BGT
        }
    }
}
