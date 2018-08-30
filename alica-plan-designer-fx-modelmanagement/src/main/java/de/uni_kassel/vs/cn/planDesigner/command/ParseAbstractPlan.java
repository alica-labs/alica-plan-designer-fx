package de.uni_kassel.vs.cn.planDesigner.command;

import de.uni_kassel.vs.cn.planDesigner.alicamodel.*;
import de.uni_kassel.vs.cn.planDesigner.modelmanagement.*;

/**
 * Parses a given file and adds the resulting object to the corresponding maps of the model manager.
 */
public class ParseAbstractPlan extends AbstractCommand {
    ModelModificationQuery modelModificationQuery;
    PlanElement oldElement;
    PlanElement newElement;

    public ParseAbstractPlan(ModelManager modelManager, ModelModificationQuery modelModificationQuery) {
        super(modelManager);
        this.modelModificationQuery = modelModificationQuery;
        oldElement = null;
        newElement = null;
    }

    @Override
    public void doCommand() {
        // 1. parse file
        // 2. delete already existing object and add new one
        switch (modelModificationQuery.getElementType()) {
            case Types.PLAN:
            case Types.MASTERPLAN:
                newElement = modelManager.parseFile(FileSystemUtil.getFile(modelModificationQuery), Plan.class);
                modelManager.replaceIncompleteTasksInEntryPoints((Plan) newElement);
                break;
            case Types.PLANTYPE:
                newElement = modelManager.parseFile(FileSystemUtil.getFile(modelModificationQuery), PlanType.class);
                modelManager.replaceIncompletePlansInPlanType((PlanType)newElement);
                break;
            case Types.BEHAVIOUR:
                newElement = modelManager.parseFile(FileSystemUtil.getFile(modelModificationQuery), Behaviour.class);
                break;
            case Types.TASKREPOSITORY:
                newElement = modelManager.parseFile(FileSystemUtil.getFile(modelModificationQuery), TaskRepository.class);
                break;
            default:
                System.err.println("ParseAbstractPlan: Parsing model eventType " + modelModificationQuery.getElementType() + " not implemented, yet!");
                return;
        }
        if (newElement instanceof Plan && ((Plan) newElement).getMasterPlan()) {
//            replaceIncompleteElements(newElement);
            modelManager.addPlanElement(Types.MASTERPLAN, newElement, null, false);
        } else {
//            replaceIncompleteElements(newElement);
            modelManager.addPlanElement(modelModificationQuery.getElementType(), newElement, null, false);
        }

    }

    @Override
    public void undoCommand() {
        if (oldElement != null) {
            // replace new object with former old one
            if (oldElement instanceof Plan && ((Plan) oldElement).getMasterPlan()) {
                modelManager.addPlanElement(Types.MASTERPLAN, oldElement, null, false);
            } else {
                modelManager.addPlanElement(modelModificationQuery.getElementType(), oldElement, null, false);
            }
        } else {
            // remove new object
            if (newElement instanceof Plan && ((Plan) newElement).getMasterPlan()) {
                modelManager.removePlanElement(Types.MASTERPLAN, newElement, null, false);
            } else {
                modelManager.removePlanElement(modelModificationQuery.getElementType(), newElement, null, false);
            }
        }
    }

//    //TODO complete for other PlanElements
//    private void replaceIncompleteElements(PlanElement newElement) {
//        if(newElement instanceof PlanType) {
//            modelManager.replaceIncompletePlansInPlanType((PlanType) newElement);
//        }
//    }
}
