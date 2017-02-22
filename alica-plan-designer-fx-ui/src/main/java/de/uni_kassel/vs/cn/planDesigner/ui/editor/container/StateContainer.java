package de.uni_kassel.vs.cn.planDesigner.ui.editor.container;

import de.uni_kassel.vs.cn.planDesigner.aggregatedModel.command.Command;
import de.uni_kassel.vs.cn.planDesigner.aggregatedModel.command.CommandStack;
import de.uni_kassel.vs.cn.planDesigner.aggregatedModel.command.change.ChangePosition;
import de.uni_kassel.vs.cn.planDesigner.alica.AbstractPlan;
import de.uni_kassel.vs.cn.planDesigner.alica.State;
import de.uni_kassel.vs.cn.planDesigner.pmlextension.uiextensionmodel.PmlUiExtension;
import de.uni_kassel.vs.cn.planDesigner.ui.editor.PlanEditorPane;
import de.uni_kassel.vs.cn.planDesigner.ui.img.AlicaIcon;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by marci on 02.12.16.
 */
public class StateContainer extends PlanElementContainer<State> implements Observable {

    public static final double STATE_RADIUS = 20.0;
    private boolean dragged;
    private List<InvalidationListener> invalidationListeners;

    public StateContainer(PmlUiExtension pmlUiExtension, State state, CommandStack commandStack) {
        super(state, pmlUiExtension, commandStack);
        invalidationListeners = new ArrayList<>();
        makeDraggable(this);
        //setBackground(new Background(new BackgroundFill(Color.PINK, CornerRadii.EMPTY, Insets.EMPTY)));
        setupContainer();
    }

    @Override
    public void setupContainer() {
        getChildren().clear();
        setLayoutX(getPmlUiExtension().getXPos());
        setLayoutY(getPmlUiExtension().getYPos());
        visualRepresentation = new Circle(STATE_RADIUS, getStateColor());
        getChildren().add(visualRepresentation);
        Text e = new Text(getContainedElement().getName());
        getChildren().add(e);
        e.setLayoutX(e.getLayoutX() - e.getLayoutBounds().getWidth()/2);
        e.setLayoutY(e.getLayoutY() - StateContainer.STATE_RADIUS);

        List<HBox> statePlans = getContainedElement()
                .getPlans()
                .stream()
                .map(p -> {
                    HBox hBox = new AbstractPlanHBox(p);
                    return hBox;
                })
                .collect(Collectors.toList());
        getChildren().addAll(statePlans);
    }

    protected Color getStateColor() {
        return Color.YELLOW;
    }

    @Override
    public CommandStack getCommandStackForDrag() {
        return commandStack;
    }

    @Override
    public void redrawElement() {
        //((PlanEditorPane) getParent()).setupPlanVisualisation();
        setupContainer();
        invalidationListeners.forEach(listener -> listener.invalidated(this));
    }

    @Override
    public Command createMoveElementCommand() {
        return new ChangePosition(getPmlUiExtension(), getContainedElement(),
                (int) (getLayoutX()),
                (int) (getLayoutY()));
    }

    @Override
    public void setDragged(boolean dragged) {
        this.dragged = dragged;
    }

    @Override
    public boolean wasDragged() {
        return dragged;
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    private class AbstractPlanHBox extends HBox {
        private AbstractPlan abstractPlan;

        public AbstractPlanHBox(AbstractPlan p) {
            super();
            this.abstractPlan = p;
            ImageView imageView = new ImageView(new AlicaIcon(p.getClass()));
            Text text = new Text(p.getName());
            this.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
            getChildren().addAll(imageView, text);
            setLayoutY(getContainedElement().getPlans().indexOf(p)*20);
            setPickOnBounds(false);
            addEventFilter(MouseEvent.MOUSE_CLICKED, event -> ((PlanEditorPane) getParent().getParent())
                    .getPlanEditorTab().getSelectedPlanElement().setValue(new Pair<>(abstractPlan, StateContainer.this)));
        }
    }

}