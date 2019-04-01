package de.unikassel.vs.alica.planDesigner.view.editor.tab.roleTab;

import de.unikassel.vs.alica.planDesigner.view.model.ViewModelElement;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;

import java.util.Iterator;
import java.util.List;

public class RoleListView extends ListView<RoleLabel> {

    public RoleListView() {
        super();
        this.setCellFactory(param -> new RoleCell());

        this.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<RoleLabel>) c -> System.out.println("RLV: selects " + getSelectedItem()));

        this.focusedProperty().addListener(
                (observable, focusedBefore, focused) -> System.out.println("RLV: focus " + getSelectedItem()));

        Platform.runLater(() -> this.getSelectionModel().select(0));
        Platform.runLater(() -> requestFocus());
    }


    public void removeElement(ViewModelElement viewModel) {
        Iterator<RoleLabel> iter = getItems().iterator();

        while (iter.hasNext()) {
            RoleLabel roleLabel = iter.next();

            if (roleLabel.getViewModelId() == viewModel.getId()) {
                iter.remove();
                return;
            }
        }
    }

    public void addElement(ViewModelElement viewModelElement) {

        Platform.runLater(() -> getItems().add(new RoleLabel(viewModelElement)));
    }

    public void addElements(List<? extends ViewModelElement> viewModelElements) {
        Platform.runLater(() -> {

            for (ViewModelElement viewModelElement : viewModelElements) {
                getItems().add(new RoleLabel(viewModelElement));
            }
        });
    }

    public ViewModelElement getSelectedItem() {
        MultipleSelectionModel<RoleLabel> selectionModel = this.getSelectionModel();
        return selectionModel.getSelectedItem().getViewModelElement();
    }

    private static class RoleCell extends ListCell<RoleLabel> {

        @Override
        protected void updateItem(RoleLabel item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null || item.getViewModelName() == null) {
                setText(null);
            } else {
                setText(item.getViewModelName());
            }
        }
    }
}
