package liveplugin.implementation

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import io.vertx.core.Future
import liveplugin.implementation.actions.UnloadPluginAction
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.PluginRunner
import spp.jetbrains.marker.plugin.LivePluginService
import spp.jetbrains.marker.plugin.impl.LivePluginServiceImpl
import spp.jetbrains.status.SourceStatus
import spp.jetbrains.status.SourceStatusService
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Needed for manually loading LivePlugin when Source++ Plugin is installed after project is opened.
 */
object LivePluginProjectLoader {

    fun projectOpened(project: Project) {
        if (project.getUserData(LivePluginService.KEY) != null) return
        val sppPluginsLocation = if (project.getUserData(LivePluginService.SPP_PLUGINS_LOCATION) == null) {
            val sppPluginsLocation = File(extractSppResources(), "plugins")
            project.putUserData(LivePluginService.SPP_PLUGINS_LOCATION, sppPluginsLocation)
            sppPluginsLocation
        } else {
            project.getUserData(LivePluginService.SPP_PLUGINS_LOCATION)!!
        }

        project.putUserData(LivePluginService.KEY, LivePluginServiceImpl(project))
        project.putUserData(LivePluginService.LIVE_PLUGIN_LOADER) {
            //load bundled plugins
            val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
            val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)
            val plugins = PluginRunner.runPlugins(
                sppPluginsLocation.toFilePath().listFiles().toLivePlugins(), dummyEvent
            ).toMutableList()

            //load user plugins
            if (project.isTrusted()) {
                val projectPath = project.basePath?.toFilePath() ?: return@putUserData
                val liveCommandsPath = projectPath + LivePluginPaths.livePluginsProjectDirName
                plugins += PluginRunner.runPlugins(liveCommandsPath.listFiles().toLivePlugins(), dummyEvent)
            }

            Future.all(plugins).onSuccess { _ ->
                SourceStatusService.getInstance(dummyEvent.project!!).update(SourceStatus.PluginsLoaded)
            }
        }
    }

    fun projectClosing(project: Project) {
        project.getUserData(LivePluginService.SPP_PLUGINS_LOCATION)?.let {
            UnloadPluginAction.unloadPlugins(project, it.toFilePath().listFiles().toLivePlugins())
        }

        val projectPath = project.basePath?.toFilePath()
        if (projectPath != null) {
            val livePluginsPath = projectPath + LivePluginPaths.livePluginsProjectDirName
            UnloadPluginAction.unloadPlugins(project, livePluginsPath.listFiles().toLivePlugins())
        }

        project.getUserData(LivePluginService.KEY)?.reset()
    }

    private fun extractSppResources(): File {
        val tmpDir = Files.createTempDirectory("spp-resources").toFile()
        tmpDir.deleteOnExit()
        val destDir = tmpDir.absolutePath

        val jar = JarFile(File(PathManager.getJarPathForClass(LivePluginProjectLoader::class.java)))
        val enumEntries: Enumeration<*> = jar.entries()
        while (enumEntries.hasMoreElements()) {
            val file = enumEntries.nextElement() as JarEntry
            if (!file.name.startsWith(".spp")) continue

            val f = File(destDir + File.separator + file.name)
            if (file.isDirectory) {
                f.mkdir()
                continue
            }

            val inputStream = jar.getInputStream(file)
            val fos = FileOutputStream(f)
            while (inputStream.available() > 0) {
                fos.write(inputStream.read())
            }
            fos.close()
            inputStream.close()
        }
        jar.close()

        return File(destDir, ".spp")
    }
}
