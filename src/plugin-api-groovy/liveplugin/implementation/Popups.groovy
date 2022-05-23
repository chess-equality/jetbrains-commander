package liveplugin.implementation

import com.intellij.ide.util.gotoByName.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.Processor
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.awt.*
import java.util.List

import static com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import static com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid

class Popups {

	static showPopupMenu(Map menuDescription, String popupTitle = "", @Nullable DataContext dataContext = null,
	                     ActionSelectionAid selectionAidMethod = SPEEDSEARCH, Closure isPreselected = { false }) {
		def contextComponent = dataContext?.getData(PlatformDataKeys.CONTEXT_COMPONENT.name) as Component
		if (contextComponent == null) {
			// Create context so that IJ doesn't throw an exception.
			def dummyComponent = WindowManager.getInstance().findVisibleFrame()
			dataContext = new MapDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, dummyComponent)
		}

		def popup = JBPopupFactory.instance.createActionGroupPopup(
			popupTitle,
			createNestedActionGroup(menuDescription),
			dataContext,
			selectionAidMethod == NUMBERING || selectionAidMethod == ALPHA_NUMBERING,
			false,
			selectionAidMethod == MNEMONICS,
			null,
			-1,
			new Condition<AnAction>() {
				@Override boolean value(AnAction anAction) {
					isPreselected(anAction)
				}
			}
		)

		if (contextComponent != null) {
			popup.showInCenterOf(contextComponent)
		} else {
			popup.showInFocusCenter()
		}
	}

	@Contract(pure = true)
	static ActionGroup createNestedActionGroup(Map description, actionGroup = new DefaultActionGroup()) {
		description.each { entry ->
			if (entry.value instanceof Closure) {
				actionGroup.add(new AnAction(entry.key.toString()) {
					@Override void actionPerformed(AnActionEvent event) {
						entry.value.call([key: entry.key, event: event])
					}
				})
			} else if (entry.value instanceof Map) {
				Map subMenuDescription = entry.value as Map
				def actionGroupName = entry.key.toString()
				def isPopup = true
				actionGroup.add(createNestedActionGroup(subMenuDescription, new DefaultActionGroup(actionGroupName, isPopup)))
			} else if (entry.value instanceof AnAction) {
				actionGroup.add(entry.value)
			}
		}
		actionGroup
	}

	static showPopupSearch(String prompt, Project project, String initialText = "", boolean lenientMatch = false,
	                       Collection items, Closure onItemChosen) {
		Closure<Collection> itemProvider = { String pattern, ProgressIndicator cancelled ->
			if (lenientMatch) {
				pattern = "*" + pattern.chars.toList().join("*")
			}
			def matcher = new NameUtil.MatcherBuilder(pattern)
				.withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
				.build()
			items.findAll { matcher.matches(it.toString()) }
		}
		showPopupSearch(prompt, project, initialText, itemProvider, onItemChosen)
	}

	static showPopupSearch(String prompt, Project project, String initialText = "",
	                       Closure<Collection> itemProvider, Closure onItemChosen) {
		def model = new SimpleChooseByNameModel(project, prompt, null) {
			@Override ListCellRenderer getListCellRenderer() {
				new ColoredListCellRenderer() {
					@Override protected void customizeCellRenderer(JList list, Object value, int index,
					                                               boolean selected, boolean hasFocus) {
						append(value.toString())
					}
				}
			}

			@Override String getElementName(Object element) {
				element.toString()
			}

			@Override String[] getNames() {
				// never called (can only be called from ChooseByNameBase)
				[].toArray()
			}

			@Override protected Object[] getElementsByName(String name, String pattern) {
				// never called (can only be called from ChooseByNameBase)
				[].toArray()
			}
		}

		def chooseByNameItemProvider = new ChooseByNameItemProvider() {
			@Override boolean filterElements(@NotNull ChooseByNameViewModel base, @NotNull String pattern, boolean everywhere,
			                                 @NotNull ProgressIndicator cancelled, @NotNull Processor<Object> consumer) {
				def items = itemProvider.call(pattern, cancelled)
				items.each { consumer.process(it) }
				!items.empty
			}

			@Override List<String> filterNames(@NotNull ChooseByNameViewModel base, @NotNull String[] names, @NotNull String pattern) {
				// never called (can only be called from ChooseByNameBase)
				names.toList()
			}
		}

		ChooseByNamePopup.createPopup(project, model, chooseByNameItemProvider, initialText).invoke(new ChooseByNamePopupComponent.Callback() {
			@Override void elementChosen(Object element) {
				onItemChosen(element)
			}
		}, ModalityState.NON_MODAL, true)
	}

}
