package de.unikassel.vs.alica.planDesigner.view.properties.bindings;

import de.unikassel.vs.alica.planDesigner.PlanDesignerApplication;
import de.unikassel.vs.alica.planDesigner.events.GuiEventType;
import de.unikassel.vs.alica.planDesigner.events.GuiModificationEvent;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiModificationHandler;
import de.unikassel.vs.alica.planDesigner.view.I18NRepo;
import de.unikassel.vs.alica.planDesigner.view.Types;
import de.unikassel.vs.alica.planDesigner.view.img.AlicaIcon;
import de.unikassel.vs.alica.planDesigner.view.model.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class VariableBindingTab extends Tab {

    private Label varLabel;
    private ComboBox<ViewModelElement> varDropDown;

    private Label subPlanLabel;
    private ComboBox<ViewModelElement> subPlanDropDown;

    private Label subVarLabel;
    private ComboBox<ViewModelElement> subVarDropDown;

    private Button addButton;

    private VariableBindingTable variableBindingTable;

    private final IGuiModificationHandler handler;
    private ViewModelElement element;

    public ViewModelElement getElement() {
        return element;
    }

    public VariableBindingTab(IGuiModificationHandler handler, String title) {
        super(title);
        this.handler = handler;

        Node root = null;
        try {
            root = FXMLLoader.load(getClass().getClassLoader().getResource("variableBindingTabContent.fxml"));

            varLabel = (Label) root.lookup("#varLabel");
            varDropDown = (ComboBox<ViewModelElement>) root.lookup("#varDropDown");
            varDropDown.setConverter(new ViewModelElementStringConverter(varDropDown, false));

            subPlanLabel = (Label) root.lookup("#subPlanLabel");
            subPlanDropDown = (ComboBox<ViewModelElement>) root.lookup("#subPlanDropDown");
            subPlanDropDown.setConverter(new ViewModelElementStringConverter(subPlanDropDown, false));
            subPlanDropDown.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ViewModelElement>() {
                @Override
                public void changed(ObservableValue<? extends ViewModelElement> observable, ViewModelElement oldValue, ViewModelElement newValue) {
                    ObservableList<VariableViewModel> variables = null;
                    switch (newValue.getType()) {
                        case Types.ANNOTATEDPLAN: {
                            AnnotatedPlanView annotatedPlanView = (AnnotatedPlanView) newValue;
                            PlanViewModel planViewModel = (PlanViewModel) handler.getViewModelElement(annotatedPlanView.getPlanId());
                            variables = planViewModel.getVariables();
                        } break;
                        case Types.PLAN: {
                            PlanViewModel planViewModel = (PlanViewModel) newValue;
                            variables = planViewModel.getVariables();
                        } break;
                        case Types.BEHAVIOUR: {
                            BehaviourViewModel behaviourViewModel = (BehaviourViewModel) newValue;
                            variables = behaviourViewModel.getVariables();
                        } break;
                        default: throw new RuntimeException(newValue.getType());
                    }
                    subVarDropDown.setItems(FXCollections.observableArrayList(variables));
                }
            });

            subVarLabel = (Label) root.lookup("#subVarLabel");
            subVarDropDown = (ComboBox<ViewModelElement>) root.lookup("#subVarDropDown");
            subVarDropDown.setConverter(new ViewModelElementStringConverter(subVarDropDown, false));
        } catch (IOException e) {
            e.printStackTrace();
        }

        addButton = (Button) root.lookup("#addButton");
        addButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                VariableViewModel selectedVar = (VariableViewModel) varDropDown.getSelectionModel().getSelectedItem();
                VariableViewModel selectedSubVar = (VariableViewModel) subVarDropDown.getSelectionModel().getSelectedItem();

                ViewModelElement selectedSubPlan = subPlanDropDown.getSelectionModel().getSelectedItem();

                if (selectedVar == null || selectedSubPlan == null || selectedSubVar == null) {
                    return;
                }
                if (isDuplicate(selectedVar, selectedSubPlan, selectedSubVar)) {
                    createDuplicateAlert();
                    return;
                }

                GuiModificationEvent event = new GuiModificationEvent(GuiEventType.ADD_ELEMENT, Types.VARIABLEBINDING, "NEW_PARAM");
                event.setParentId(element.getId());

                HashMap<String, Long> relatedObjects = new HashMap<>();
                relatedObjects.put(Types.VARIABLE, selectedVar.getId());
                relatedObjects.put(Types.VARIABLEBINDING, selectedSubVar.getId());
                relatedObjects.put(Types.PLAN, selectedSubPlan.getId());

                event.setRelatedObjects(relatedObjects);
                handler.handle(event);
            }
        });

        variableBindingTable = (VariableBindingTable) root.lookup("#variableBindingTable");
        variableBindingTable.setController(this);

        this.setContent(root);
        this.init();
    }

    private void createDuplicateAlert() {
        I18NRepo i18NRepo = I18NRepo.getInstance();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Duplicate!");
        alert.setContentText(i18NRepo.getString("label.error.duplicateParametrisation"));


        ButtonType closeBtn = new ButtonType(i18NRepo.getString("action.close"));

        alert.getButtonTypes().setAll(closeBtn);

        alert.initOwner(PlanDesignerApplication.getPrimaryStage());
        alert.showAndWait();
    }

    private boolean isDuplicate(VariableViewModel selectedVar, ViewModelElement selectedSubPlan, VariableViewModel selectedSubVar) {
        ObservableList<VariableBindingViewModel> parametrisations = ((HasVariableBinding) element).getVariableBindings();

        if (selectedSubPlan.getType().equals(Types.ANNOTATEDPLAN)) {
            selectedSubPlan = handler.getViewModelElement(((AnnotatedPlanView) selectedSubPlan).getPlanId());
        }

        for (VariableBindingViewModel variableBindingViewModel : parametrisations) {
            if (variableBindingViewModel.getSubPlan().getId() == selectedSubPlan.getId())
                if (variableBindingViewModel.getSubVariable().getId() == selectedSubVar.getId())
                    if (variableBindingViewModel.getVariable().getId() == selectedVar.getId()) {
                        return true;
                    }
        }
        return false;
    }

    private void init() {
        I18NRepo repo = I18NRepo.getInstance();

        // ---- LABEL ----
        varLabel.setText(repo.getString("label.caption.variables"));
        subPlanLabel.setText(repo.getString("label.column.subplan"));
        subVarLabel.setText(repo.getString("label.column.subVariable"));

        // ---- BUTTON ----
        addButton.setGraphic(new ImageView(new AlicaIcon(AlicaIcon.ADD, AlicaIcon.Size.SMALL)));

        // ---- DROPDOWN ----
        if (varDropDown.getItems().isEmpty()) {
            varDropDown.setItems(FXCollections.observableArrayList(new ViewModelElement(0L, "--empty--", null)));
        }

        if (subPlanDropDown.getItems().isEmpty()) {
            subPlanDropDown.setItems(FXCollections.observableArrayList(new ViewModelElement(0L, "--empty--", null)));
        }

        if (subVarDropDown.getItems().isEmpty()) {
            subVarDropDown.setItems(FXCollections.observableArrayList(new ViewModelElement(0L, "--empty--", null)));
        }
    }

    public void setViewModel(ViewModelElement element) {
        if (!(element instanceof HasVariableBinding)) {
            return;
        }
        this.element = element;
        switch (element.getType()) {
            case Types.PLANTYPE: {
                PlanTypeViewModel planTypeViewModel = (PlanTypeViewModel) element;

                ArrayList<ViewModelElement> elements = new ArrayList<>(((PlanTypeViewModel) element).getVariables());

                for(PlanViewModel planViewModel: planTypeViewModel.getAllPlans()) {
                    elements.addAll(planViewModel.getVariables());
                }

                varDropDown.setItems(FXCollections.observableArrayList(elements));
                varDropDown.setConverter(new ViewModelElementStringConverter(varDropDown, true));

                subPlanDropDown.setItems(FXCollections.observableArrayList(planTypeViewModel.getPlansInPlanType()));
                subVarDropDown.setItems(FXCollections.observableArrayList(new ViewModelElement(0L, "please select plan first", null)));


            } break;
            case Types.STATE: {
                StateViewModel stateViewModel = (StateViewModel) element;
                varDropDown.setItems(FXCollections.observableArrayList(((PlanViewModel) handler.getViewModelElement(element.getParentId())).getVariables()));
                subPlanDropDown.setItems(FXCollections.observableArrayList(stateViewModel.getAbstractPlans()));
                subVarDropDown.setItems(FXCollections.observableArrayList(new ViewModelElement(0L, "please select plan first", null)));

            } break;
            default:{
                System.err.println("VariableBindingTab: Unrecognized ViewElementType");
            }
        }

        ObservableList variableBindings = ((HasVariableBinding) element).getVariableBindings();
        variableBindingTable.setItems(variableBindings);
//        variableBindings.addListener(new ListChangeListener() {
//            @Override
//            public void onChanged(Change c) {
//                for (TableColumn column: variableBindingTable.getColumns()) {
//                    variableBindingTable.resizeColumn(column, 1);
//                }
//                if (c.getList().size() > 0) {
//                    variableBindingTable.setVisible(true);
//                } else {
//                    variableBindingTable.setVisible(false);
//                }
//            }
//        });
//
//        if (variableBindings.isEmpty()) {
//            variableBindingTable.setVisible(false);
//        } else {
//            variableBindingTable.setVisible(true);
//        }
    }

    public void deleteParametrisation(long id) {
        GuiModificationEvent event = new GuiModificationEvent(GuiEventType.DELETE_ELEMENT, Types.VARIABLEBINDING, "NEW_PARAM");
        event.setElementId(id);
        event.setParentId(element.getId());
        handler.handle(event);
    }

    private class ViewModelElementStringConverter extends StringConverter<ViewModelElement> {

        private final ComboBox<ViewModelElement> comboBox;
        private final boolean withPlan;

        ViewModelElementStringConverter(ComboBox<ViewModelElement> comboBox, boolean withPlan) {
            this.comboBox = comboBox;
            this.withPlan = withPlan;
        }

        @Override
        public String toString(ViewModelElement viewModelElement) {
            if (withPlan) {
                return getComplexPlanVariableName((VariableViewModel) viewModelElement);
            }
            return viewModelElement.getName();
        }

        @Override
        public ViewModelElement fromString(String string) {
            string = string.substring(string.lastIndexOf(":"), string.length());

            for (ViewModelElement viewModelElement: comboBox.getItems()) {
                if (viewModelElement.getName().equals(string)) {
                    return viewModelElement;
                }
            }
            return null;
        }
    }

    String getComplexPlanVariableName(VariableViewModel variableViewModel) {
        ViewModelElement plan = null;
        for (PlanViewModel planViewModel : ((PlanTypeViewModel) element).getAllPlans()) {
            if (planViewModel.getVariables().contains(variableViewModel)) {
                plan = planViewModel;
                break;
            }
        }

        if (plan == null) {
            plan = element;
        }

        return plan.getName() + ":" + variableViewModel.getName();
    }
}