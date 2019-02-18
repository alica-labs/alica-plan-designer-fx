package de.unikassel.vs.alica.planDesigner.command.add;

import de.unikassel.vs.alica.planDesigner.alicamodel.AbstractPlan;
import de.unikassel.vs.alica.planDesigner.alicamodel.EntryPoint;
import de.unikassel.vs.alica.planDesigner.alicamodel.Plan;
import de.unikassel.vs.alica.planDesigner.alicamodel.Task;
import de.unikassel.vs.alica.planDesigner.command.AbstractCommand;
import de.unikassel.vs.alica.planDesigner.events.ModelEvent;
import de.unikassel.vs.alica.planDesigner.events.ModelEventType;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelManager;
import de.unikassel.vs.alica.planDesigner.modelmanagement.Types;

public class AddTaskToEntryPoint extends AbstractCommand {
    protected EntryPoint entryPoint;
    protected Task task;

    public AddTaskToEntryPoint(ModelManager modelManager, Task task, EntryPoint entryPoint) {
        super(modelManager);
        this.task = task;
        this.entryPoint = entryPoint;
    }

    @Override
    public void doCommand() {
//        state.addAbstractPlan(abstractPlan);
        entryPoint.setTask(task);

        //event for updateView
        Plan plan = (Plan) entryPoint.getPlan();
        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ADD, plan, Types.TASK);
        event.setParentId(task.getId());
        event.setNewValue(entryPoint);
        modelManager.fireEvent(event);
    }

    @Override
    public void undoCommand() { entryPoint.setTask(null); }
}