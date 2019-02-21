package de.unikassel.vs.alica.planDesigner.command.create;

import de.unikassel.vs.alica.planDesigner.alicamodel.EntryPoint;
import de.unikassel.vs.alica.planDesigner.alicamodel.Plan;
import de.unikassel.vs.alica.planDesigner.alicamodel.Task;
import de.unikassel.vs.alica.planDesigner.command.UiPositionCommand;
import de.unikassel.vs.alica.planDesigner.events.ModelEvent;
import de.unikassel.vs.alica.planDesigner.events.ModelEventType;
import de.unikassel.vs.alica.planDesigner.events.UiExtensionModelEvent;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelManager;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelModificationQuery;
import de.unikassel.vs.alica.planDesigner.modelmanagement.Types;

public class CreateEntryPoint extends UiPositionCommand {

    protected EntryPoint entryPoint;

    public CreateEntryPoint(ModelManager manager, ModelModificationQuery mmq) {
        super(manager, mmq);
        this.entryPoint = createEntryPoint();
        this.uiElement = createUiElement(mmq.getParentId(), this.entryPoint);
    }

    protected EntryPoint createEntryPoint() {
        EntryPoint entryPoint = new EntryPoint();
        entryPoint.setPlan((Plan) this.modelManager.getPlanElement(mmq.getParentId()));
        entryPoint.setTask((Task) this.modelManager.getPlanElement(mmq.getRelatedObjects().get(Types.TASK)));
        return entryPoint;
    }

    @Override
    public void doCommand() {
        this.uiExtension.add(entryPoint, uiElement);
        this.modelManager.storePlanElement(Types.ENTRYPOINT, this.entryPoint, false);
        this.fireEvent(ModelEventType.ELEMENT_CREATED, this.entryPoint);
    }

    @Override
    public void undoCommand() {
        this.uiExtension.remove(entryPoint);
        this.modelManager.dropPlanElement(Types.ENTRYPOINT, this.entryPoint, false);
        this.fireEvent(ModelEventType.ELEMENT_DELETED, this.entryPoint);
    }
}
