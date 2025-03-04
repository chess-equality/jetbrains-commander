package liveplugin.implementation

import com.intellij.ide.actions.CreateDirectoryCompletionContributor
import com.intellij.ide.actions.CreateDirectoryCompletionContributor.Variant
import com.intellij.ide.scratch.RootType
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.indexing.IndexableSetContributor
import liveplugin.implementation.LivePluginPaths.livePluginsCompiledPath
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.common.Icons.pluginIcon
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.delete
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins
import org.jetbrains.jps.model.module.UnknownSourceRootType

// For consistency with "IDE Consoles" it's good to have live plugins under "Scratches and Consoles"
// but it's also used for enabling Kotlin intentions in live plugin, i.e. outside of project
// (since change in IJ 2022.1: Anna Kozlova* 22/12/2021, 17:21 [kotlin] disable intentions which modifies code in libraries (KTIJ-20543))
class ScratchLivePluginRootType : RootType("LivePlugin", "Live Plugins") {
    override fun substituteIcon(project: Project, file: VirtualFile) =
        if (file.toFilePath().isPluginFolder()) pluginIcon else super.substituteIcon(project, file)

    companion object {
        init {
            System.setProperty(PathManager.PROPERTY_SCRATCH_PATH + "/LivePlugin", livePluginsPath.value)
        }
    }
}

class LivePluginDeletedListener : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
        events.filter { it is VFileDeleteEvent && it.file.toFilePath().isPluginFolder() }
            .filter { event -> event.file?.toFilePath() != null }
            .forEach {
                handleLivePluginDeleted(it)
            }
    }

    private fun handleLivePluginDeleted(event: VFileEvent) {
        val livePlugins = listOf(event).map { event.file!!.toFilePath() }.toLivePlugins()
        if (livePlugins.isNotEmpty()) {
            runLaterOnEdt {
                unloadPlugins(livePlugins)
                livePlugins.forEach { plugin ->
                    (livePluginsCompiledPath + plugin.id).toVirtualFile()?.delete()
                }
            }
        }
    }
}

class LivePluginDirectoryCompletionContributor : CreateDirectoryCompletionContributor {
    override fun getDescription() = "Project specific live plugins"
    override fun getVariants(directory: PsiDirectory): List<Variant> =
        if (directory.project.basePath != directory.virtualFile.path) {
            if (directory.virtualFile.name == ".spp" && directory.project.basePath == directory.virtualFile.parent.path) {
                listOf(Variant("plugins", UnknownSourceRootType.getInstance("LivePlugin")))
            } else emptyList()
        } else listOf(Variant(".spp/plugins", UnknownSourceRootType.getInstance("LivePlugin")))
}

class MakePluginFilesAlwaysEditable : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile) = file.toFilePath().isPluginFolder()
}

class EnableSyntaxHighlighterInLivePlugins : SyntaxHighlighterProvider {
    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? {
        if (project == null || file == null || !file.toFilePath().isPluginFolder()) return null
        val language = LanguageUtil.getLanguageForPsi(project, file) ?: return null
        return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
    }
}

class UsageTypeExtension : UsageTypeProvider {
    override fun getUsageType(element: PsiElement): UsageType? {
        val file = PsiUtilCore.getVirtualFile(element) ?: return null
        return if (!file.toFilePath().isPluginFolder()) null
        else UsageType { "Usage in liveplugin" }
    }
}

class IndexSetContributor : IndexableSetContributor() {
    override fun getAdditionalRootsToIndex(): Set<VirtualFile> {
        return mutableSetOf(livePluginsPath.toVirtualFile() ?: return HashSet())
    }
}

class UseScopeExtension : UseScopeEnlarger() {
    override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
        val useScope = element.useScope
        return if (useScope is LocalSearchScope) null else LivePluginsSearchScope.getScopeInstance(element.project)
    }

    private class LivePluginsSearchScope(project: Project) : GlobalSearchScope(project) {
        override fun getDisplayName() = "LivePlugins"
        override fun contains(file: VirtualFile) = file.toFilePath().isPluginFolder()
        override fun isSearchInModuleContent(aModule: Module) = false
        override fun isSearchInLibraries() = false

        companion object {
            private val SCOPE_KEY = NotNullLazyKey.create<LivePluginsSearchScope, Project>("LIVEPLUGIN_SEARCH_SCOPE_KEY") { project ->
                LivePluginsSearchScope(project)
            }

            fun getScopeInstance(project: Project): GlobalSearchScope = SCOPE_KEY.getValue(project)
        }
    }
}
