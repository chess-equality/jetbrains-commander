package liveplugin.implementation.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.UrlClassLoader
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Result
import liveplugin.implementation.common.asFailure
import liveplugin.implementation.common.asSuccess
import liveplugin.implementation.pluginrunner.AnError.LoadingError
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter
import java.util.*

interface ExecutablePlugin

interface PluginRunner {
    val scriptName: String

    fun setup(plugin: LivePlugin, project: Project?): Result<ExecutablePlugin, AnError>

    fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, AnError>

    object ClasspathAddition {
        @Suppress("UnstableApiUsage")
        fun createClassLoaderWithDependencies(
            additionalClasspath: List<File>,
            pluginDescriptors: List<IdeaPluginDescriptor>,
            plugin: LivePlugin
        ): Result<ClassLoader, LoadingError> {
            val additionalPaths = additionalClasspath.map { file -> file.toPath() }.onEach { path ->
                if (!path.exists()) return LoadingError("Didn't find plugin dependency '${path.toFile().absolutePath}'.").asFailure()
            }
            val parentClassLoaders = pluginDescriptors.mapNotNull { it.pluginClassLoader } + PluginRunner::class.java.classLoader

            return PluginClassLoader_Fork(
                additionalPaths,
                ClassPath(additionalPaths, UrlClassLoader.build(), null, false),
                parentClassLoaders.toTypedArray(),
                DefaultPluginDescriptor(plugin.id),
                PluginManagerCore::class.java.classLoader,
                null,
                null,
                emptyList()
            ).asSuccess()
        }

        fun findPluginDescriptorsOfDependencies(lines: List<String>, keyword: String): List<Result<IdeaPluginDescriptor, String>> {
            val userPlugins = lines.filter { line -> line.startsWith(keyword) }
                .map { line -> line.replace(keyword, "").trim { it <= ' ' } }
                .map { PluginManagerCore.getPlugin(PluginId.getId(it))?.asSuccess() ?: "Failed to find dependent plugin '$it'.".asFailure() }
            return userPlugins.toMutableList().apply {
                add(PluginManagerCore.getPlugin(PluginId.getId("com.sourceplusplus.plugin.intellij"))!!.asSuccess())
            }
        }

        fun List<IdeaPluginDescriptor>.withTransitiveDependencies(): List<IdeaPluginDescriptor> {
            val result = HashSet<IdeaPluginDescriptor>()
            val queue = LinkedList(this)
            while (queue.isNotEmpty()) {
                val descriptor = queue.remove()
                if (descriptor !in result) {
                    result.add(descriptor)

                    val dependenciesDescriptors1 = descriptor.dependencies.mapNotNullTo(HashSet()) {
                        if (it.isOptional) null else PluginManagerCore.getPlugin(it.pluginId)
                    }

                    @Suppress("UnstableApiUsage") // This is a "temporary" hack for https://youtrack.jetbrains.com/issue/IDEA-206274
                    val dependenciesDescriptors2 =
                        if (descriptor !is IdeaPluginDescriptorImpl) emptyList()
                        else descriptor.dependencies.plugins.mapNotNullTo(HashSet()) { PluginManagerCore.getPlugin(it.id) }

                    val descriptors = (dependenciesDescriptors1 + dependenciesDescriptors2)
                        .filter { it.pluginId != CORE_ID }.distinctBy { it.pluginId }

                    queue.addAll(descriptors)
                }
            }
            return result.toList()
        }

        fun findClasspathAdditions(lines: List<String>, keyword: String, environment: Map<String, String>): List<Result<List<File>, String>> {
            return lines.filter { line -> line.startsWith(keyword) }
                .map { line -> line.replace(keyword, "").trim { it <= ' ' } }
                .map { line -> line.inlineEnvironmentVariables(environment) }
                .map { path ->
                    val files = findMatchingFiles(path).map { File(it) }
                    if (files.isEmpty()) path.asFailure() else files.asSuccess()
                }
        }

        private fun findMatchingFiles(pathAndPattern: String): List<String> {
            if (File(pathAndPattern).exists()) return listOf(pathAndPattern)

            val separatorIndex = pathAndPattern.lastIndexOf(File.separator)
            val path = pathAndPattern.substring(0, separatorIndex + 1)
            val pattern = pathAndPattern.substring(separatorIndex + 1)

            val files = File(path).listFiles(GlobFilenameFilter(pattern) as FileFilter) ?: emptyArray()
            return files.map { it.absolutePath }
        }

        private fun String.inlineEnvironmentVariables(environment: Map<String, String>): String {
            var result = this
            environment.forEach { (key, value) ->
                result = result.replace("$$key", value)
            }
            return result
        }
    }
}

class Binding(
    val project: Project?,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
) {
    companion object
}

fun systemEnvironment(): Map<String, String> = HashMap(System.getenv())

sealed class AnError {
    data class LoadingError(val message: String = "", val throwable: Throwable? = null) : AnError()
    data class RunningError(val throwable: Throwable) : AnError()
}

fun List<LivePlugin>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    any { livePlugin ->
        pluginRunners.any { runner ->
            livePlugin.path.allFiles().any { it.name == runner.scriptName }
        }
    }
