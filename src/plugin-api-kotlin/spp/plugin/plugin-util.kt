@file:Suppress("unused")

package spp.plugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy.DEFAULT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import liveplugin.PluginUtil
import liveplugin.PluginUtil.openUrlInEditor
import liveplugin.implementation.Console
import liveplugin.implementation.Editors
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import java.awt.Component

/**
 * Shows popup balloon notification with the specified [message] (which can include HTML tags),
 * [title], [notificationType] and [groupDisplayId] (see `IDE Settings - Appearance & Behavior - Notifications`).
 *
 * Under the hood, this function sends IDE notification event
 * which is displayed as a "balloon" and added to the `Event Log` console.
 */
fun show(
    message: Any?,
    title: String = "",
    notificationType: NotificationType = INFORMATION,
    groupDisplayId: String = "Live Plugin",
    notificationAction: NotificationAction? = null
) {
    PluginUtil.show(message, title, notificationType, groupDisplayId, notificationAction)
}

fun Project.showInConsole(
    message: Any?,
    consoleTitle: String = "",
    contentType: ConsoleViewContentType = Console.guessContentTypeOf(message),
    scrollTo: Int = -1
): ConsoleView =
    Console.showInConsole(message, consoleTitle, this, contentType, scrollTo)

fun Document.executeCommand(project: Project, description: String? = null, callback: Document.() -> Unit) {
    runOnEdtWithWriteLock {
        val command = { callback(this) }
        CommandProcessor.getInstance().executeCommand(project, command, description, null, DEFAULT, this)
    }
}

fun LivePluginScript.registerIntention(intention: IntentionAction): IntentionAction =
    PluginUtil.registerIntention(pluginDisposable, intention)

fun LivePluginScript.registerInspection(inspection: InspectionProfileEntry) {
    PluginUtil.registerInspection(pluginDisposable, inspection)
}

fun openInBrowser(url: String) =
    BrowserUtil.browse(url)

fun Project.openInIdeBrowser(url: String, title: String = "") =
    HTMLEditorProvider.openEditor(this, title, url, null)

fun Project.openInEditor(filePath: String) {
    openUrlInEditor("file://${filePath}", this)
}

val logger: Logger = Logger.getInstance("LivePlugin")

val AnActionEvent.contextComponent: Component?
    get() = PlatformDataKeys.CONTEXT_COMPONENT.getData(this.dataContext)

val AnActionEvent.editor: Editor?
    get() = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(this.dataContext)

val AnActionEvent.document: Document?
    get() = editor?.document

val AnActionEvent.virtualFile: VirtualFile?
    get() = CommonDataKeys.VIRTUAL_FILE.getData(this.dataContext)

val AnActionEvent.psiFile: PsiFile?
    get() = CommonDataKeys.PSI_FILE.getData(this.dataContext)

val AnActionEvent.psiElement: PsiElement?
    get() = CommonDataKeys.PSI_ELEMENT.getData(this.dataContext)

val VirtualFile.document: Document?
    get() = FileDocumentManager.getInstance().getDocument(this)

val Project.currentEditor: Editor?
    get() = Editors.currentEditorIn(this)

val Project.currentFile: VirtualFile?
    get() = FileEditorManagerEx.getInstanceEx(this).currentFile

val Project.currentPsiFile: PsiFile?
    get() = currentFile?.let { PsiManager.getInstance(this).findFile(it) }

val Project.currentDocument: Document?
    get() = currentFile?.document
