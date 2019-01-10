package de.unikassel.vs.alica.planDesigner.view.editor.tab;

import de.unikassel.vs.alica.planDesigner.controller.MainWindowController;
import de.unikassel.vs.alica.planDesigner.events.GuiEventType;
import de.unikassel.vs.alica.planDesigner.events.GuiModificationEvent;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiModificationHandler;
import de.unikassel.vs.alica.planDesigner.view.editor.container.AbstractPlanHBox;
import de.unikassel.vs.alica.planDesigner.view.editor.container.StateContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.container.TransitionContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.planTab.PropertiesConditionsVariablesPane;
import de.unikassel.vs.alica.planDesigner.view.model.SerializableViewModel;
import de.unikassel.vs.alica.planDesigner.view.model.StateViewModel;
import de.unikassel.vs.alica.planDesigner.view.model.ViewModelElement;
import de.unikassel.vs.alica.planDesigner.controller.IsDirtyWindowController;
import de.unikassel.vs.alica.planDesigner.view.I18NRepo;
import de.unikassel.vs.alica.planDesigner.view.editor.container.AbstractPlanElementContainer;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Pair;

import java.util.List;

public abstract class AbstractPlanTab extends Tab implements IEditorTab{

    protected I18NRepo i18NRepo;
    protected SimpleObjectProperty<List<Pair<ViewModelElement, AbstractPlanElementContainer>>> selectedPlanElements;

    protected IGuiModificationHandler guiModificationHandler;
    protected SerializableViewModel serializableViewModel;
    protected ObservableList<Node> visualRepresentations;

    protected PropertiesConditionsVariablesPane propertiesConditionsVariablesPane;
    protected SplitPane splitPane;

    public AbstractPlanTab(SerializableViewModel serializableViewModel, IGuiModificationHandler handler) {
        // set Tab Caption to name of file, represented by this Tab
        super(serializableViewModel.getName());
        this.i18NRepo = I18NRepo.getInstance();
        this.guiModificationHandler = handler;
        this.serializableViewModel = serializableViewModel;
        propertiesConditionsVariablesPane = new PropertiesConditionsVariablesPane();
        propertiesConditionsVariablesPane.setText(serializableViewModel.getName());
        propertiesConditionsVariablesPane.setViewModelElement(serializableViewModel);

        initSelectedPlanElements(serializableViewModel);

        // onAddElement close tab handlerinterfaces
        setClosable(true);
        setOnCloseRequest(e -> {
            // popup for trying to close dirty tab
            if (serializableViewModel.isDirty()) {
                IsDirtyWindowController.createIsDirtyWindow(this, e);
            }
        });

        serializableViewModel.dirtyProperty().addListener((observable, oldValue, newValue) -> {
            if (!getText().contains("*") && newValue) {
                this.setText(getText() + "*");
            } else if (getText().contains("*") && !newValue) {
                this.setText(getText().substring(0, getText().length()-1));
            }
        });

        propertiesConditionsVariablesPane.setMaxHeight(20000);

        this.splitPane = new SplitPane(propertiesConditionsVariablesPane);
        this.splitPane.setOrientation(Orientation.VERTICAL);

        setContent(splitPane);
    }

    public boolean isDirty() {return serializableViewModel.isDirty();}

    public SimpleObjectProperty<List<Pair<ViewModelElement, AbstractPlanElementContainer>>> getSelectedPlanElements() {
        return selectedPlanElements;
    }

    /**
     * initialization for the selected element property, which indicates what elements are selected.
     * specializations for specific selections of {@link AbstractPlanElementContainer}s can be found under
     * AbstractPlanElementContainer#getMouseClickedEventHandler(PlanElement)
     *
     * @param editablePathPair
     */
    // TODO: Review necessary, due to MVC pattern adaption.
    protected void initSelectedPlanElements(ViewModelElement editablePathPair) {
          selectedPlanElements = new SimpleObjectProperty<>(FXCollections.observableArrayList());
          selectedPlanElements.get().add(new Pair<>(editablePathPair, null));
          selectedPlanElements.addListener((observable, oldValue, newValue) -> {
//            if (newValue == null) {
//                // TODO: cannot return here because this avoid deleting selectedEffect on oldValue
//                return;
//            }
            // set selectedEffect on selected elements...

            // ... for state container and its containing elements (really special)
            DropShadow selectedEffect = createSelectedEffect();
            if (newValue.size() == 1 && newValue.get(0).getKey() instanceof StateViewModel
                    && newValue.get(0).getValue() != null
                    && newValue.get(0).getValue() instanceof StateContainer) {
                ((StateContainer) newValue.get(0).getValue()).getStatePlans()
                        .stream()
                        .filter(abstractPlanHBox -> abstractPlanHBox.getAbstractPlan()
                                .equals(newValue.get(0).getKey()))
                        .findFirst().orElseGet(() -> new AbstractPlanHBox(newValue.get(0).getKey(),
                        (StateContainer) newValue.get(0).getValue()))
                        .setEffect(createSelectedEffect());
            } else {
                newValue.forEach(selectedPlanElementPair -> {
                    AbstractPlanElementContainer planElementContainer = selectedPlanElementPair.getValue();
                    if (planElementContainer != null) {
                        planElementContainer.setEffect(selectedEffect);
                    }
                });
            }
            // ... for transitions
            if (newValue.size() == 1 && newValue.get(0).getValue() instanceof TransitionContainer) {
                ((TransitionContainer) newValue.get(0).getValue()).setPotentialDraggableNodesVisible(true);
            }

            if ((oldValue == null)) {
                return;
            }
            oldValue.forEach(selectedPlanElementPair -> {
                AbstractPlanElementContainer planElementContainer = selectedPlanElementPair.getValue();
                if (planElementContainer != null) {
                    // this is weird! If I use planElementContainer.setEffectToStandard() nothing happens..
                    if (planElementContainer.getModelElement() == oldValue.get(0).getKey()) {
                        planElementContainer.setEffect(null);
                    }
                    if (planElementContainer instanceof StateContainer) {
                        ((StateContainer) planElementContainer)
                                .getStatePlans()
                                .forEach(abstractPlanHBox -> {
                                    if (abstractPlanHBox.getAbstractPlan() != newValue.get(0).getKey()) {
                                        abstractPlanHBox.setEffect(null);
                                    }
                                });
                    }
                }
                if (planElementContainer instanceof StateContainer) {
                    ((StateContainer) planElementContainer)
                            .getStatePlans()
                            .forEach(abstractPlanHBox -> {
                                if (abstractPlanHBox.getAbstractPlan() != newValue.get(0).getKey()) {
                                    abstractPlanHBox.setEffect(null);
                                }
                            });
                }
            });

            if (oldValue.size() == 1 && oldValue.get(0).getValue() instanceof TransitionContainer) {
                ((TransitionContainer) oldValue.get(0).getValue()).setPotentialDraggableNodesVisible(false);
            }
        });
    }

    public void clearSelectedElements() {
        selectedPlanElements.get().forEach(element -> {
            if (element.getValue() == null) {
                return;
            }
            element.getValue().setEffect(null);
        });
        selectedPlanElements.get().clear();
        visualRepresentations.clear();
    }

    private DropShadow createSelectedEffect() {
        DropShadow value = new DropShadow(20, new Color(0, 0.4, 0.9, 0.9));
        value.setBlurType(BlurType.ONE_PASS_BOX);
        value.setSpread(0.45);
        return value;
    }

    public ViewModelElement getSerializableViewModel() {
        return serializableViewModel;
    }

    @Override
    public boolean representsViewModelElement(ViewModelElement viewModelElement) {
        return this.serializableViewModel.equals(viewModelElement);
    }

    public void revertChanges() {
        GuiModificationEvent event = new GuiModificationEvent(GuiEventType.RELOAD_ELEMENT, serializableViewModel.getType(), serializableViewModel.getName());
        event.setElementId(serializableViewModel.getId());
        MainWindowController.getInstance().getGuiModificationHandler().handle(event);
    }
}
