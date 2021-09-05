package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsActions.ActionText
import liveplugin.pluginrunner.kotlin.LivePluginScript
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * Registers action with the specified [id] (which must be unique in IDE)
 * where the action is represented as a [function] that takes an [AnActionEvent] and creates a side effect.
 *
 * You can specify [keyStroke] for the action. For example, "ctrl shift H",
 * or for a shortcut with double keystroke "alt C, alt D" (hold alt, press C, release C, press D).
 * Modification keys are lowercase, e.g. ctrl, alt, shift, cmd. Letters are uppercase.
 * Other keys are uppercase based on the constant names in [java.awt.event.KeyEvent] without "VK_" prefix,
 * e.g. ENTER, ESCAPE, SPACE, LEFT, UP, F1, F12. For details, see [javax.swing.KeyStroke.getKeyStroke] javadoc.
 *
 * You can also specify [actionGroupId] to add actions to existing menus,
 * e.g. "ToolsMenu" corresponds to `Main menu - Tools`.
 *
 * Note that the action is registered with the `pluginDisposable` and will be automatically unregistered
 * when the plugin is unloaded or evaluated again. See https://plugins.jetbrains.com/docs/intellij/disposers.html.
 */
fun LivePluginScript.registerAction(
    id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    function: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, pluginDisposable, AnAction(id, function))
}

fun LivePluginScript.registerAction(
    id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, pluginDisposable, action)
}

fun registerAction(
    id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    disposable: Disposable,
    function: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, disposable, AnAction(id, function))
}

fun registerAction(
    id: String,
    // TODO @ActionText text: String = id,
    keyboardShortcut: KeyboardShortcut? = null,
    actionGroupId: String? = null,
    disposable: Disposable,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    val actionManager = ActionManager.getInstance()
    val keymapManager = KeymapManager.getInstance()

    if ((actionManager.getAction(id) != null)) error("Action '$id' is already registered")
    action.templatePresentation.setText(id, true)

    val actionGroup =
        if (actionGroupId == null) null
        else actionManager.getAction(actionGroupId) as? DefaultActionGroup

    if (keyboardShortcut != null) keymapManager.activeKeymap.addShortcut(id, keyboardShortcut)
    actionManager.registerAction(id, action)
    actionGroup?.add(action)

    disposable.whenDisposed {
        actionGroup?.remove(action)
        actionManager.unregisterAction(id)
        if (keyboardShortcut != null) keymapManager.activeKeymap.removeShortcut(id, keyboardShortcut)
    }
    return action
}

fun AnAction(@ActionText text: String? = null, f: (AnActionEvent) -> Unit) =
    object: AnAction(text) {
        override fun actionPerformed(event: AnActionEvent) = f(event)
        override fun isDumbAware() = true
    }

fun PopupActionGroup(@ActionText name: String, vararg actions: AnAction) =
    DefaultActionGroup(name, actions.toList()).also { it.isPopup = true }

fun ActionGroup.createPopup(
    dataContext: DataContext? = null,
    selectionAidMethod: JBPopupFactory.ActionSelectionAid = JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
    showNumbers: Boolean = selectionAidMethod == JBPopupFactory.ActionSelectionAid.NUMBERING || selectionAidMethod == JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
    showDisabledActions: Boolean = false,
    isPreselected: (AnAction) -> Boolean = { false }
): ListPopup =
    JBPopupFactory.getInstance().createActionGroupPopup(
        templatePresentation.text,
        this,
        dataContext ?: MapDataContext(mapOf(PlatformDataKeys.CONTEXT_COMPONENT.name to JPanel())), // prevent createActionGroupPopup() from crashing without context component
        showNumbers,
        showDisabledActions,
        selectionAidMethod == JBPopupFactory.ActionSelectionAid.MNEMONICS,
        null,
        -1,
        isPreselected
    )

private fun String.toKeyboardShortcut(): KeyboardShortcut? {
    val parts = split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .map {
            it.replace(".", "PERIOD")
                .replace("-", "MINUS")
                .replace("=", "EQUALS")
                .replace(";", "SEMICOLON")
                .replace("/", "SLASH")
                .replace("[", "OPEN_BRACKET")
                .replace("\\", "BACK_SLASH")
                .replace("]", "CLOSE_BRACKET")
                .replace("`", "BACK_QUOTE")
                .replace("'", "QUOTE")
        }
    if (parts.isEmpty()) return null

    val firstKeystroke = KeyStroke.getKeyStroke(parts[0]) ?: error("Invalid keystroke '${this}'")
    val secondKeystroke = if (parts.size > 1) KeyStroke.getKeyStroke(parts[1]) else null
    return KeyboardShortcut(firstKeystroke, secondKeystroke)
}

/**
 * @see liveplugin.PluginUtil.assertNoNeedForEdtOrWriteActionWhenUsingActionManager
 */
inline fun <T> noNeedForEdtOrWriteActionWhenUsingActionManager(f: () -> T) = f()