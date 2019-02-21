package de.unikassel.vs.alica.planDesigner.command.add;

import de.unikassel.vs.alica.planDesigner.alicamodel.Condition;
import de.unikassel.vs.alica.planDesigner.alicamodel.Variable;
import de.unikassel.vs.alica.planDesigner.command.Command;
import de.unikassel.vs.alica.planDesigner.events.ModelEventType;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelManager;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelModificationQuery;

public class AddVariableToCondition extends Command {
    protected Condition condition;
    protected Variable variable;

    public  AddVariableToCondition(ModelManager modelManager, ModelModificationQuery mmq) {
        super(modelManager, mmq);
        this.condition = (Condition) modelManager.getPlanElement(mmq.getParentId());
        this.variable = (Variable) modelManager.getPlanElement(variable.getId());
    }

    @Override
    public void doCommand() {
        condition.getVariables().add(variable);
        this.fireEvent(ModelEventType.ELEMENT_ADDED, this.variable);
    }

    @Override
    public void undoCommand() {
        condition.getVariables().remove(variable);
        this.fireEvent(ModelEventType.ELEMENT_REMOVED, this.variable);
    }
}
