package de.unikassel.vs.alica.planDesigner.view.editor.tools.transition;

import de.unikassel.vs.alica.planDesigner.controller.MainWindowController;
import de.unikassel.vs.alica.planDesigner.events.GuiEventType;
import de.unikassel.vs.alica.planDesigner.events.GuiModificationEvent;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiModificationHandler;
import de.unikassel.vs.alica.planDesigner.view.Types;
import de.unikassel.vs.alica.planDesigner.view.editor.container.EntryPointContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.container.StateContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.planTab.PlanTab;
import de.unikassel.vs.alica.planDesigner.view.editor.tools.AbstractTool;
import de.unikassel.vs.alica.planDesigner.view.editor.tools.DraggableHBox;
import de.unikassel.vs.alica.planDesigner.view.editor.tools.EditorToolBar;
import de.unikassel.vs.alica.planDesigner.view.model.StateViewModel;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseEvent;
import de.unikassel.vs.alica.planDesigner.view.img.AlicaCursor;
import javafx.scene.layout.VBox;



public class InitTransitionTool extends AbstractTool {

    private boolean initial = true;
    private EntryPointContainer start;
    private StateContainer finish;

    public InitTransitionTool(TabPane workbench, PlanTab planTab) {
        super(workbench, planTab);
    }

//    public InitStateConnection createNewObject() {
//        return new InitStateConnection();
//    }

    @Override
    public DraggableHBox createToolUI() {
        DraggableHBox draggableHBox = new InitStateConnectionHBox();
        setDraggableHBox(draggableHBox);
        imageCursor = new AlicaCursor(AlicaCursor.Type.initstateconnection);
        forbiddenCursor = new AlicaCursor(AlicaCursor.Type.forbidden_initstateconnection);
        addCursor = new AlicaCursor(AlicaCursor.Type.add_initstateconnection);
        return draggableHBox;
    }

    private class InitStateConnectionHBox extends DraggableHBox {
        public InitStateConnectionHBox() {
            setIcon(Types.INITSTATECONNECTION);
            setOnDragDetected(Event::consume);
            setOnMouseClicked(event -> startTool());
        }
    }

    public void draw() {
        // ((PlanTab) planEditorTabPane.getSelectionModel().getSelectedItem()).getPlanEditorGroup().setupPlanVisualisation();
    }

    @Override
    protected void initHandlerMap() {
        if (customHandlerMap.isEmpty()) {
            customHandlerMap.put(MouseEvent.MOUSE_MOVED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    Node target = (Node) event.getTarget();
                    Parent parent = target.getParent();
                    if(initial){
                        if (parent instanceof EntryPointContainer) {
                            setCursor(addCursor);
                        } else {
                            setCursor(forbiddenCursor);
                        }
                    } else {
                        if (parent instanceof StateContainer) {
                            setCursor(addCursor);
                        } else {
                            setCursor(forbiddenCursor);
                        }
                    }

                    if (parent instanceof DraggableHBox || parent instanceof EditorToolBar || parent instanceof VBox) {
                        setCursor(Cursor.DEFAULT);
                    }
                }
            });

            customHandlerMap.put(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {

                    Node target = (Node) event.getTarget();
                    Parent parent = target.getParent();

                    if (parent instanceof DraggableHBox) {
                        endTool();
                    }

                    if (handleNonPrimaryButtonEvent(event)) {
                        return;
                    }

                    if (((Node) event.getTarget()).getParent() instanceof EntryPointContainer) {
                        if (initial) {
                            start =  ((EntryPointContainer) ((Node) event.getTarget()).getParent());
                            initial = false;
                        }
                    } else if (((Node) event.getTarget()).getParent() instanceof StateContainer && initial == false) {
                        StateViewModel outState = ((StateContainer) ((Node) event.getTarget()).getParent()).getState();                        initial = true;
//                    SetStateForEntryPoint command = new SetStateForEntryPoint(start.getModelElementId(), finish.getModelElementId());
//                    MainWindowController.getInstance()
//                            .getCommandStack()
//                            .storeAndExecute(command);

//                        IGuiModificationHandler handler = MainWindowController.getInstance().getGuiModificationHandler();
//
//                        GuiModificationEvent guiEvent = new GuiModificationEvent(GuiEventType.ADD_ELEMENT, Types.INITSTATECONNECTION, null);
//
//                        HashMap<String, Long> relatedObjects = new HashMap<>();
//                        relatedObjects.put("inState", start.getModelElement().getId());
//                        relatedObjects.put("outState", outState.getId());
//
//                        guiEvent.setRelatedObjects(relatedObjects);
//                        guiEvent.setParentId(InitTransitionTool.this.getPlanTab().getSerializableViewModel().getId());
//                        handler.handle(guiEvent);
                    }
                }
            });
        }
    }



//    /**
//     * This is a pseudo class because init transitions are no real objects.
//     */
//    static final class InitStateConnection extends PlanElementImpl {
//
//    }
}