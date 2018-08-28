package de.uni_kassel.vs.cn.planDesigner.view.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;

public class SynchronisationViewModel extends ViewModelElement {

    protected ObservableList<TransitionViewModel> transitions;

    public SynchronisationViewModel(long id, String name, String type) {
        super(id, name, type);
        this.transitions = FXCollections.observableArrayList(new ArrayList<>());
    }

    public ObservableList<TransitionViewModel> getTransitions() {
        return transitions;
    }
}
