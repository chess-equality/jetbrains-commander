package liveplugin.toolwindow.util

import com.intellij.openapi.vfs.VirtualFile
import liveplugin.LivePluginAppComponent.Companion.readSampleScriptFile
import liveplugin.LivePluginPaths
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import java.io.IOException

data class ExamplePlugin(val path: String, val pluginId: String, val filePaths: List<String>) {
    constructor(path: String, pluginId: String, vararg filePaths: String): this(path, pluginId, filePaths.toList())
}

object GroovyExamples {
    val helloWorld = ExamplePlugin(groovyExamplesPath, "hello-world", "plugin.groovy")
    val ideActions = ExamplePlugin(groovyExamplesPath, "ide-actions", "plugin.groovy")
    val modifyDocument = ExamplePlugin(groovyExamplesPath, "modify-document", "plugin.groovy")
    val popupMenu = ExamplePlugin(groovyExamplesPath, "popup-menu", "plugin.groovy")

    val all = listOf(
        helloWorld,
        ideActions,
        modifyDocument,
        popupMenu,
        ExamplePlugin(groovyExamplesPath, "popup-search", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "tool-window", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "toolbar-widget", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "java-inspection", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "java-intention", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "project-files-stats", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "misc-util", "util/AClass.groovy", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "additional-classpath", "plugin.groovy"),
        ExamplePlugin(groovyExamplesPath, "integration-test", "plugin-test.groovy", "plugin.groovy"),
    )
}

object KotlinExamples {
    val all = listOf(
        ExamplePlugin(kotlinExamplesPath, "hello-world", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "ide-actions", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "modify-document", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "popup-menu", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "kotlin-intention", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "kotlin-inspection", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "java-intention", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "java-inspection", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "additional-classpath", "plugin.kts"),
        ExamplePlugin(kotlinExamplesPath, "multiple-src-files", "foo.kt", "bar/bar.kt", "plugin.kts"),
    )
}

fun ExamplePlugin.installPlugin(handleError: (e: Exception, pluginPath: String) -> Unit, whenCreated: (VirtualFile) -> Unit = {}) {
    filePaths.forEach { relativeFilePath ->
        val resourceDirPath = "$path/$pluginId/"
        try {
            val text = readSampleScriptFile("$resourceDirPath/$relativeFilePath")
            val (parentPath, fileName) = splitIntoPathAndFileName("${LivePluginPaths.livePluginsPath}/$pluginId/$relativeFilePath")
            createFile(parentPath, fileName, text, whenCreated)
        } catch (e: IOException) {
            handleError(e, resourceDirPath)
        }
    }
}

private fun splitIntoPathAndFileName(filePath: String): Pair<String, String> {
    val index = filePath.lastIndexOf("/")
    return Pair(filePath.substring(0, index), filePath.substring(index + 1))
}
