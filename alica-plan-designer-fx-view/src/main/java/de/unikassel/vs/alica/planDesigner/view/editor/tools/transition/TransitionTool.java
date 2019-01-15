package de.unikassel.vs.alica.planDesigner.view.editor.tools.transition;

import de.unikassel.vs.alica.planDesigner.controller.MainWindowController;
import de.unikassel.vs.alica.planDesigner.events.GuiChangePositionEvent;
import de.unikassel.vs.alica.planDesigner.events.GuiEventType;
import de.unikassel.vs.alica.planDesigner.events.GuiModificationEvent;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiModificationHandler;
import de.unikassel.vs.alica.planDesigner.view.Types;
import de.unikassel.vs.alica.planDesigner.view.editor.container.StateContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.planTab.PlanTab;
import de.unikassel.vs.alica.planDesigner.view.editor.tools.AbstractTool;
import de.unikassel.vs.alica.planDesigner.view.editor.tools.DraggableHBox;
import de.unikassel.vs.alica.planDesigner.view.img.AlicaCursor;
import de.unikassel.vs.alica.planDesigner.view.model.PlanViewModel;
import de.unikassel.vs.alica.planDesigner.view.model.StateViewModel;
import de.unikassel.vs.alica.planDesigner.view.model.TransitionViewModel;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import java.util.HashMap;
import java.util.LinkedList;

public class TransitionTool extends AbstractTool {

    private int initial = 0;
    private LinkedList<Point2D> bendPoints = new LinkedList<>();

    private StateViewModel inState;
    private Cursor bendPointCursor = new AlicaCursor(AlicaCursor.Type.bendpoint_transition);

    public TransitionTool(TabPane workbench, PlanTab planTab) {
        super(workbench, planTab);
    }

    @Override
    protected void initHandlerMap() {
        if (customHandlerMap.isEmpty()) {

            customHandlerMap.put(MouseEvent.MOUSE_MOVED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    Node target = (Node) event.getTarget();
                    Parent parent = target.getParent();
                    if (parent instanceof StateContainer) {
                        setCursor(addCursor);
                    } else if (target instanceof StackPane){
                        if (initial > 1) {
                            setCursor(bendPointCursor);
                        }
                        else {
                            setCursor(imageCursor);
                        }
                    } else {
                        setCursor(forbiddenCursor);
                    }
                }
            });


            customHandlerMap.put(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (initial > 1) {
                        Node target = (Node) event.getTarget();
                        Node parent = target.getParent();
                        if (parent instanceof StateContainer) {
                            // SET ENDPOINT
                            StateViewModel outState = ((StateContainer) ((Node) event.getTarget()).getParent()).getState();

                            IGuiModificationHandler handler = MainWindowController.getInstance().getGuiModificationHandler();

                            GuiModificationEvent guiEvent = new GuiModificationEvent(GuiEventType.ADD_ELEMENT, Types.TRANSITION, null);

                            HashMap<String, Long> relatedObjects = new HashMap<>();
                            relatedObjects.put(TransitionViewModel.INSTATE, inState.getId());
                            relatedObjects.put(TransitionViewModel.OUTSTATE, outState.getId());

                            guiEvent.setRelatedObjects(relatedObjects);
                            guiEvent.setParentId(TransitionTool.this.getPlanTab().getSerializableViewModel().getId());
                            handler.handle(guiEvent);

                            long transitionID = 0;
                            for (TransitionViewModel transition : ((PlanViewModel) TransitionTool.this.getPlanTab().getSerializableViewModel()).getTransitions()) {
                                if (transition.getOutState().getId() == outState.getId()
                                        && transition.getInState().getId() == inState.getId()) {
                                    transitionID = transition.getId();
                                }
                            }

                            for (Point2D point : bendPoints) {
                                GuiChangePositionEvent bendEvent = new GuiChangePositionEvent(GuiEventType.ADD_ELEMENT, Types.BENDPOINT, null);
                                bendEvent.setNewX((int) point.getX());
                                bendEvent.setNewY((int) point.getY());
                                bendEvent.setParentId(TransitionTool.this.getPlanTab().getSerializableViewModel().getId());
                                HashMap<String, Long> related = new HashMap<>();
                                related.put(Types.TRANSITION, transitionID);
                                bendEvent.setRelatedObjects(related);
                                handler.handle(bendEvent);
                            }

                            TransitionTool.this.reset();
                        } else {
                            // ADD BENDPOINT
                            Point2D eventTargetCoordinates = TransitionTool.this.getLocalCoordinatesFromEvent(event);

                            if (eventTargetCoordinates == null) {
                                event.consume();
                            }
                            bendPoints.add(eventTargetCoordinates);
                        }
                    } else {
                        initial = 1;
                        Node target = null;
                        try {
                            target = (Node) event.getTarget();
                            if ((target.getParent() instanceof StateContainer)) {
                                // SET STARTPOINT
                                inState = ((StateContainer) ((Node) event.getTarget()).getParent()).getState();
                            }
                        } catch (ClassCastException e) {
                            e.printStackTrace();
                        }
                    }
                    initial++;
                }
            });
        }
    }

    @Override
    public void endTool() {
        reset();
        super.endTool();
    }

    private void reset() {
        initial = 0;
        inState = null;
        bendPoints.clear();
    }

    @Override
    public DraggableHBox createToolUI() {
        DraggableHBox draggableHBox = new DraggableHBox();
        draggableHBox.setIcon(Types.TRANSITION);
        setDraggableHBox(draggableHBox);
        imageCursor = new AlicaCursor(AlicaCursor.Type.transition, 0, 8);
        forbiddenCursor = new AlicaCursor(AlicaCursor.Type.forbidden_transition, 8, 8);
        addCursor = new AlicaCursor(AlicaCursor.Type.add_transition, 8, 8);
        return draggableHBox;
    }
}