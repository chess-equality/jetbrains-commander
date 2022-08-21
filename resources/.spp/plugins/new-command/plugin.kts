import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

class NewCommandCommand(project: Project) : LiveCommand(project) {
    override val name = message("New Command")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Add new custom live command" + "</span></html>"
    override val params: List<String> = listOf("Command Name")

    override fun trigger(context: LiveCommandContext) {
        if (context.args.isEmpty()) {
            show("Missing command name", notificationType = NotificationType.ERROR)
            return
        }

        runWriteAction {
            val commandName = context.args.joinToString(" ")
            val commandDir = commandName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "plugin.kts", IdeUtil.kotlinFileType, getNewCommandScript(commandName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/plugins/$commandDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(commandName: String): String {
        val properCommandName = commandName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import com.intellij.openapi.project.Project
            import spp.command.*
            import spp.jetbrains.sourcemarker.PluginUI.*
            import spp.plugin.*

            class ${properCommandName}Command(project: Project) : LiveCommand(project) {
                override val name = "$commandName"
                override val description = "<html><span style=\"color: ${'$'}{getCommandTypeColor()}\">" +
                        "My custom live command" + "</span></html>"

                override fun trigger(context: LiveCommandContext) {
                    show("Hello world")
                }
            }

            registerCommand(${properCommandName}Command(project))
        """.trimIndent() + "\n"
    }
}

registerCommand(NewCommandCommand(project))
