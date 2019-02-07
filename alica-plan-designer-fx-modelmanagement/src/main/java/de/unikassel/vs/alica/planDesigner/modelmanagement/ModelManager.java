package de.unikassel.vs.alica.planDesigner.modelmanagement;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.unikassel.vs.alica.planDesigner.alicamodel.*;
import de.unikassel.vs.alica.planDesigner.command.*;
import de.unikassel.vs.alica.planDesigner.command.add.*;
import de.unikassel.vs.alica.planDesigner.command.change.ChangeAttributeValue;
import de.unikassel.vs.alica.planDesigner.command.change.ChangePosition;
import de.unikassel.vs.alica.planDesigner.command.change.ConnectEntryPointsWithState;
import de.unikassel.vs.alica.planDesigner.command.change.ConnectSynchronizationWithTransition;
import de.unikassel.vs.alica.planDesigner.command.create.CreateBehaviour;
import de.unikassel.vs.alica.planDesigner.command.create.CreatePlan;
import de.unikassel.vs.alica.planDesigner.command.create.CreatePlanType;
import de.unikassel.vs.alica.planDesigner.command.create.CreateTask;
import de.unikassel.vs.alica.planDesigner.command.delete.*;
import de.unikassel.vs.alica.planDesigner.events.*;
import de.unikassel.vs.alica.planDesigner.modelMixIns.*;
import de.unikassel.vs.alica.planDesigner.uiextensionmodel.BendPoint;
import de.unikassel.vs.alica.planDesigner.uiextensionmodel.PlanModelVisualisationObject;
import de.unikassel.vs.alica.planDesigner.uiextensionmodel.PmlUiExtension;
import de.unikassel.vs.alica.planDesigner.uiextensionmodel.PmlUiExtensionMap;
import javafx.collections.ListChangeListener;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.*;

public class ModelManager implements Observer {

    private static final Logger LOG = LogManager.getLogger(ModelManager.class);

    private String plansPath;
    private String tasksPath;
    private String rolesPath;

    private HashMap<Long, PlanElement> planElementMap;
    private HashMap<Long, Plan> planMap;
    private HashMap<Long, Behaviour> behaviourMap;
    private HashMap<Long, PlanType> planTypeMap;
    private TaskRepository taskRepository;

    private List<IModelEventHandler> eventHandlerList;
    private CommandStack commandStack;

    /**
     * This list remembers elements that should be saved, in order
     * to ignore filesystem-modification-events created by the
     * save command. Entries gets deleted through the events.
     */
    private HashMap<Long, Integer> elementsSavedMap;

    private HashMap<Long, Integer> elementDeletedMap;

    /**
     * In this map all necessary information about the visualisation
     * of a {@link Plan} is saved and can be accessed by the id of the
     * corresponding Plan
     */
    private HashMap<Long, PlanModelVisualisationObject> planModelVisualisationObjectMap;

    private ObjectMapper objectMapper;

    public ModelManager() {
        planElementMap = new HashMap<>();
        planMap = new HashMap<>();
        behaviourMap = new HashMap<>();
        planTypeMap = new HashMap<>();
        eventHandlerList = new ArrayList<>();
        commandStack = new CommandStack();
        commandStack.addObserver(this);
        elementsSavedMap = new HashMap<>();
        elementDeletedMap = new HashMap<>();
        planModelVisualisationObjectMap = new HashMap<>();

        setupObjectMapper();
    }

    private void setupObjectMapper() {
        objectMapper = new ObjectMapper();

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        objectMapper.addMixIn(EntryPoint.class, EntryPointMixIn.class);
        objectMapper.addMixIn(Parametrisation.class, ParametrisationMixIn.class);
        objectMapper.addMixIn(AnnotatedPlan.class, AnnotatedPlanMixIn.class);
        objectMapper.addMixIn(Plan.class, PlanMixIn.class);
        objectMapper.addMixIn(Quantifier.class, QuantifierMixIn.class);
        objectMapper.addMixIn(State.class, StateMixIn.class);
        objectMapper.addMixIn(Synchronization.class, SynchronizationMixIn.class);
        objectMapper.addMixIn(Task.class, TaskMixIn.class);
        objectMapper.addMixIn(TaskRepository.class, TaskRepositoryMixIn.class);
        objectMapper.addMixIn(Transition.class, TransitionMixIn.class);
        objectMapper.addMixIn(PlanModelVisualisationObject.class, PlanModelVisualizationObjectMixIn.class);
        objectMapper.addMixIn(PmlUiExtensionMap.class, PmlUiExtensionMapMixIn.class);
        objectMapper.addMixIn(BendPoint.class, BendPointMixIn.class);
    }

    public void setPlansPath(String plansPath) {
        this.plansPath = plansPath;
    }

    public void setTasksPath(String tasksPath) {
        this.tasksPath = tasksPath;
    }

    public void setRolesPath(String rolesPath) {
        this.rolesPath = rolesPath;
    }

    public ArrayList<Plan> getPlans() {
        return new ArrayList<>(planMap.values());
    }

    public ArrayList<PlanType> getPlanTypes() {
        return new ArrayList<>(planTypeMap.values());
    }

    public ArrayList<Behaviour> getBehaviours() {
        return new ArrayList<>(behaviourMap.values());
    }

    public ArrayList<PlanElement> getPlanElements() {
        return new ArrayList<>(planElementMap.values());
    }

    public PlanElement getPlanElement(long id) {
        if (planElementMap.containsKey(id)) {
            return planElementMap.get(id);
        } else {
            return null;
        }
    }

    public PlanElement getPlanElement(String absolutePath) {
        String name = absolutePath.substring(absolutePath.lastIndexOf(File.separator) + 1);
        String[] split = name.split("\\.");
        if (split[1].equals(FileSystemUtil.BEHAVIOUR_ENDING)) {
            for (Behaviour beh : behaviourMap.values()) {
                if (beh.getName().equals(split[0])) {
                    return beh;
                }
            }
        } else if (split[1].equals(FileSystemUtil.PLAN_ENDING)) {
            for (Plan plan : planMap.values()) {
                if (plan.getName().equals(split[0])) {
                    return plan;
                }
            }
        } else if (split[1].equals(FileSystemUtil.PLANTYPE_ENDING)) {
            for (PlanType pt : planTypeMap.values()) {
                if (pt.getName().equals(split[0])) {
                    return pt;
                }
            }
        } else if (split[1].equals(FileSystemUtil.TASKREPOSITORY_ENDING)) {
            if (taskRepository.getName().equals(split[0])) {
                return taskRepository;
            }
        } else {
            System.out.println("ModelManager: trying to get PlanElement for unsupported ending: " + split[1]);
        }
        return null;
    }

    public ArrayList<Condition> getConditions() {
        ArrayList<Condition> conditions = new ArrayList<>();
        for (Plan plan : planMap.values()) {
            conditions.add(plan.getPreCondition());
            conditions.add(plan.getRuntimeCondition());
            for (Transition transition : plan.getTransitions()) {
                conditions.add(transition.getPreCondition());
            }
            for (State state : plan.getStates()) {
                if (state instanceof TerminalState) {
                    conditions.add(((TerminalState) state).getPostCondition());
                }
            }
        }
        for (Behaviour behaviour : behaviourMap.values()) {
            conditions.add(behaviour.getPreCondition());
            conditions.add(behaviour.getRuntimeCondition());
            conditions.add(behaviour.getPostCondition());
        }

        // remove all null values inserted before
        conditions.removeIf(Objects::isNull);
        return conditions;
    }

    public HashMap<Long, PlanModelVisualisationObject> getPlanModelVisualisationObjectMap() {
        return planModelVisualisationObjectMap;
    }

    public void addListener(IModelEventHandler eventHandler) {
        eventHandlerList.add(eventHandler);
    }

    public void loadModelFromDisk() {
        unloadModel();
        loadModelFromDisk(tasksPath);
        loadModelFromDisk(plansPath);
        loadModelFromDisk(rolesPath);
        replaceIncompleteReferences();
    }

    private void unloadModel() {
        planElementMap.clear();
        planMap.clear();
        behaviourMap.clear();
        planTypeMap.clear();
        commandStack.getRedoStack().clear();
        commandStack.getUndoStack().clear();
        elementsSavedMap.clear();
        elementDeletedMap.clear();
        planModelVisualisationObjectMap.clear();
    }

    private void replaceIncompleteReferences() {
        for (Plan plan : planMap.values()) {
            replaceIncompleteTasksInEntryPoints(plan);
            replaceIncompleteAbstractPlansInStates(plan);
            replaceIncompleteStatesAndSynchronizationsInTransitions(plan);
            replaceIncompleteBendPointTransitions(plan);
            if (plan.getMasterPlan()) {
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_PARSED, plan, Types.MASTERPLAN));
            } else {
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_PARSED, plan, Types.PLAN));
            }
        }
        for (PlanType planType : getPlanTypes()) {
            replaceIncompletePlansInPlanType(planType);
            fireEvent(new ModelEvent(ModelEventType.ELEMENT_PARSED, planType, Types.PLANTYPE));
        }
        for (PlanModelVisualisationObject planModelVisualisationObject : planModelVisualisationObjectMap.values()) {
            replaceIncompletePlanElementsInPlanModelVisualisationObject(planModelVisualisationObject);
        }
    }

    /**
     * This method could be superfluous, as "loadModelFile" is maybe called by the file watcher.
     * Anyway, temporarily this is a nice method for testing and is therefore called in the constr.
     */
    private void loadModelFromDisk(String directory) {
        File plansDirectory = new File(directory);
        if (!plansDirectory.exists()) {
            return;
        }

        File[] allModelFiles = plansDirectory.listFiles();
        if (allModelFiles == null || allModelFiles.length == 0) {
            return;
        }

        for (File modelFile : allModelFiles) {
            if (modelFile.isDirectory()) {
                loadModelFromDisk(modelFile.getPath());
            }
            loadModelFile(modelFile);
        }
    }

    /**
     * Determines the file ending and loads the model object, accordingly.
     * The constructed object is inserted in the corresponding model element maps, if no
     * duplicated ID was found.
     * <p>
     * This method should only be called through a command or within the model manager itself!
     *
     * @param modelFile the file to be parsed by jackson
     */
    private void loadModelFile(File modelFile) throws RuntimeException {
        if (modelFile.isDirectory()) {
            return;
        }

        if (modelFile.length() == 0) {
            return;
        }

        String path = modelFile.toString();
        int pointIdx = path.lastIndexOf('.');
        String ending = "";
        if (pointIdx != -1) {
            ending = path.substring(pointIdx + 1, path.length());
        }

        boolean correctTopLevelFolder = correctTopLevelFolder(path);
        if (!correctTopLevelFolder) {
            return;
        }

        try {
            switch (ending) {
                case FileSystemUtil.PLAN_ENDING:
                    Plan plan = objectMapper.readValue(modelFile, Plan.class);
                    plan.dirtyProperty().addListener((observable, oldValue, newValue) -> {
                        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, plan, Types.PLAN);
                        event.setChangedAttribute("dirty");
                        event.setNewValue(newValue);
                        this.fireEvent(event);
                    });
                    plan.registerDirtyFlag();

                    if (planElementMap.containsKey(plan.getId())) {
                        throw new RuntimeException("PlanElement ID duplication found! ID is: " + plan.getId());
                    } else {
                        planElementMap.put(plan.getId(), plan);
                        planMap.put(plan.getId(), plan);
                        for (State state : plan.getStates()) {
                            planElementMap.put(state.getId(), state);
                        }
                        for (Transition transition : plan.getTransitions()) {
                            planElementMap.put(transition.getId(), transition);
                        }
                        for (EntryPoint ep : plan.getEntryPoints()) {
                            planElementMap.put(ep.getId(), ep);
                        }
                        for (Synchronization sync : plan.getSynchronizations()) {
                            planElementMap.put(sync.getId(), sync);
                        }
                        for (Variable variable : plan.getVariables()) {
                            planElementMap.put(variable.getId(), variable);
                        }
                        if (plan.getPreCondition() != null) {
                            planElementMap.put(plan.getPreCondition().getId(), plan.getPreCondition());
                        }
                        if (plan.getRuntimeCondition() != null) {
                            planElementMap.put(plan.getRuntimeCondition().getId(), plan.getRuntimeCondition());
                        }
                    }
                    break;
                case FileSystemUtil.BEHAVIOUR_ENDING:
                    Behaviour behaviour = objectMapper.readValue(modelFile, Behaviour.class);
                    behaviour.dirtyProperty().addListener((observable, oldValue, newValue) -> {
                        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, behaviour, Types.BEHAVIOUR);
                        event.setChangedAttribute("dirty");
                        event.setNewValue(newValue);
                        this.fireEvent(event);
                    });
                    if (planElementMap.containsKey(behaviour.getId())) {
                        throw new RuntimeException("PlanElement ID duplication found! ID is: " + behaviour.getId());
                    } else {
                        planElementMap.put(behaviour.getId(), behaviour);
                        behaviourMap.put(behaviour.getId(), behaviour);
                        for (Variable variable : behaviour.getVariables()) {
                            planElementMap.put(variable.getId(), variable);
                        }
                        if (behaviour.getPreCondition() != null) {
                            planElementMap.put(behaviour.getPreCondition().getId(), behaviour.getPreCondition());
                        }
                        if (behaviour.getRuntimeCondition() != null) {
                            planElementMap.put(behaviour.getRuntimeCondition().getId(), behaviour.getRuntimeCondition());
                        }
                        if (behaviour.getPostCondition() != null) {
                            planElementMap.put(behaviour.getPostCondition().getId(), behaviour.getPostCondition());
                        }
                        fireEvent(new ModelEvent(ModelEventType.ELEMENT_PARSED, behaviour, Types.BEHAVIOUR));
                    }
                    break;
                case FileSystemUtil.PLANTYPE_ENDING:
                    PlanType planType = objectMapper.readValue(modelFile, PlanType.class);
                    planType.dirtyProperty().addListener((observable, oldValue, newValue) -> {
                        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, planType, Types.PLANTYPE);
                        event.setChangedAttribute("dirty");
                        event.setNewValue(newValue);
                        this.fireEvent(event);
                    });
                    planType.registerDirtyFlag();
                    if (planElementMap.containsKey(planType.getId())) {
                        throw new RuntimeException("PlanElement ID duplication found! ID is: " + planType.getId());
                    } else {
                        planElementMap.put(planType.getId(), planType);
                        planTypeMap.put(planType.getId(), planType);
                        for (AnnotatedPlan annotatedPlan : planType.getPlans()) {
                            planElementMap.put(annotatedPlan.getId(), annotatedPlan);
                        }
                    }
                    break;
                case FileSystemUtil.TASKREPOSITORY_ENDING:
                    TaskRepository taskRepository = objectMapper.readValue(modelFile, TaskRepository.class);
                    taskRepository.dirtyProperty().addListener((observable, oldValue, newValue) -> {
                        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, taskRepository, Types.TASKREPOSITORY);
                        event.setChangedAttribute("dirty");
                        event.setNewValue(newValue);
                        this.fireEvent(event);
                    });
                    taskRepository.getTasks().addListener(new ListChangeListener<Task>() {
                        @Override
                        public void onChanged(Change<? extends Task> change) {
                            while (change.next()) {
                                taskRepository.setDirty(true);
                            }
                        }
                    });
                    for (Task task : taskRepository.getTasks()) {
                        task.nameProperty().addListener((observable, oldValue, newValue) -> {
                            taskRepository.setDirty(true);
                        });
                        task.commentProperty().addListener((observable, oldValue, newValue) -> {
                            taskRepository.setDirty(true);
                        });
                    }
                    if (planElementMap.containsKey(taskRepository.getId())) {
                        throw new RuntimeException("PlanElement ID duplication found! ID is: " + taskRepository.getId());
                    } else {
                        long defaultTaskId = ParsedModelReferences.getInstance().defaultTaskId;
                        for (Task task : taskRepository.getTasks()) {
                            task.setTaskRepository(taskRepository);
                            if (task.getId() == defaultTaskId) {
                                taskRepository.setDefaultTask(task);
                            }
                            planElementMap.put(task.getId(), task);
                        }
                        planElementMap.put(taskRepository.getId(), taskRepository);
                        this.taskRepository = taskRepository;
                        fireEvent(new ModelEvent(ModelEventType.ELEMENT_PARSED, this.taskRepository, Types.TASKREPOSITORY));
                    }
                    break;

                case FileSystemUtil.ROLESET_ENDING:
                case FileSystemUtil.CAPABILITY_DEFINITION_ENDING:
                case FileSystemUtil.ROLESET_GRAPH_ENDING:
                case FileSystemUtil.ROLES_DEFINITION_ENDING:
                    LOG.error("Parsing roles not implemented, yet!");
                    break;
                case FileSystemUtil.PLAN_EXTENSION_ENDING:
                    PlanModelVisualisationObject planModelVisualisationObject = objectMapper.readValue(modelFile, PlanModelVisualisationObject.class);
                    long id = planModelVisualisationObject.getPlan().getId();
                    planModelVisualisationObjectMap.put(id, planModelVisualisationObject);
                    break;
                default:
                    LOG.error("Received file with unknown file ending, for parsing. File is: '" + path + "'");
            }
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            System.err.println("PlanDesigner-ModelManager: Unable to parse " + modelFile);
            System.err.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Replaces all incomplete Tasks attached to the plan's entrypoints by already parsed ones
     *
     * @param plan
     */
    public void replaceIncompleteTasksInEntryPoints(Plan plan) {
        for (EntryPoint ep : plan.getEntryPoints()) {
            for (Task task : taskRepository.getTasks()) {
                if (task.getId() == ep.getTask().getId()) {
                    ep.setTask(task);
                    break;
                }
            }
        }
    }

    public void replaceIncompleteBendPointTransitions(Plan plan) {
        PlanModelVisualisationObject visualisationObject = getCorrespondingPlanModelVisualisationObject(plan.getId());
        HashMap<PlanElement, PmlUiExtension> extensionMap = visualisationObject.getPmlUiExtensionMap().getExtension();
        ArrayList<Long> incompleteBendPointTransitions = ParsedModelReferences.getInstance().incompleteBendPointTransitions;
        for (Map.Entry<PlanElement, PmlUiExtension> extensionEntry : extensionMap.entrySet()) {
            long transitionId = extensionEntry.getKey().getId();
            if (incompleteBendPointTransitions.contains(transitionId)) {
                for (Transition transition : plan.getTransitions()) {
                    if (transition.getId() == transitionId) {
                        for (BendPoint bendPoint : extensionEntry.getValue().getBendPoints()) {
                            bendPoint.setTransition(transition);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void replaceIncompleteAbstractPlansInStates(Plan plan) {
        ParsedModelReferences refs = ParsedModelReferences.getInstance();
        for (State state : plan.getStates()) {
            for (int i = 0; i < state.getPlans().size(); i++) {
                if (refs.incompleteAbstractPlansInStates.contains(state.getPlans().get(i).getId())) {
                    state.getPlans().set(i, (AbstractPlan) planElementMap.get(state.getPlans().get(i).getId()));
                }
            }
        }
    }

    private void replaceIncompleteStatesAndSynchronizationsInTransitions(Plan plan) {
        ParsedModelReferences refs = ParsedModelReferences.getInstance();
        for (Transition transition : plan.getTransitions()) {
            if (refs.incompleteAbstractPlansInStates.contains(transition.getInState().getId())) {
                transition.setInState((State) planElementMap.get(transition.getInState().getId()));
            }
            if (refs.incompleteAbstractPlansInStates.contains(transition.getOutState().getId())) {
                transition.setOutState((State) planElementMap.get(transition.getOutState().getId()));
            }
            if (transition.getSynchronization() != null && refs.incompleteSynchronizationsInTransitions.contains(transition.getSynchronization().getId())) {
                transition.setSynchronization((Synchronization) planElementMap.get(transition.getSynchronization().getId()));
            }
        }
    }


    /**
     * Replaces all incomplete Plans in given PlanType by already parsed ones
     */
    public void replaceIncompletePlansInPlanType(PlanType planType) {
        List<AnnotatedPlan> annotatedPlans = planType.getPlans();
        for (int i = 0; i < annotatedPlans.size(); i++) {
            if (ParsedModelReferences.getInstance().incompletePlansInPlanTypes.contains(annotatedPlans.get(i).getPlan().getId())) {
                annotatedPlans.get(i).setPlan(planMap.get(annotatedPlans.get(i).getPlan().getId()));
            }
        }
    }

    /**
     * Replace all incomplete {@link PlanElement}s in given {@link PlanModelVisualisationObject} with already parsed ones.
     * <p>
     * These contain the {@link Plan} and the {@link PlanElement}s, that are used as keys in the {@link PmlUiExtensionMap}
     *
     * @param planModelVisualisationObject the {@link PlanModelVisualisationObject} with incomplete references
     */
    public void replaceIncompletePlanElementsInPlanModelVisualisationObject(PlanModelVisualisationObject planModelVisualisationObject) {
        //Set the correct Plan
        Plan completePlan = planMap.get(planModelVisualisationObject.getPlan().getId());
        if (completePlan != null) {
            planModelVisualisationObject.setPlan(completePlan);
        }

        //Set the correct PlanElements the PmlUiExtensionMap
        HashMap<PlanElement, PmlUiExtension> oldMap = planModelVisualisationObject.getPmlUiExtensionMap().getExtension();
        HashMap<PlanElement, PmlUiExtension> newMap = new HashMap<>();

        for (PlanElement incomplete : oldMap.keySet()) {
            long id = incomplete.getId();
            PmlUiExtension value = oldMap.get(incomplete);
            PlanElement complete = getPlanElement(id);

            if (complete != null) {
                newMap.put(complete, value);

                UiExtensionModelEvent event = new UiExtensionModelEvent(ModelEventType.ELEMENT_PARSED, complete, null);
                event.setExtension(value);
                fireUiExtensionModelEvent(event);
            }
        }

        planModelVisualisationObject.getPmlUiExtensionMap().setExtensionHashMap(newMap);
    }

    private boolean correctTopLevelFolder(String path) {

        if (path.endsWith(FileSystemUtil.BEHAVIOUR_ENDING) || path.endsWith(FileSystemUtil.PLAN_ENDING) || path.endsWith(FileSystemUtil.PLANTYPE_ENDING)) {
            if (path.contains(rolesPath) || path.contains(tasksPath)) {
                return false;
            }
        } else if (path.endsWith(FileSystemUtil.TASKREPOSITORY_ENDING)) {
            if (path.contains(plansPath) || path.contains(rolesPath)) {
                return false;
            }
        } else if (path.endsWith(FileSystemUtil.CAPABILITY_DEFINITION_ENDING) || path.endsWith(FileSystemUtil.ROLES_DEFINITION_ENDING) || path.endsWith
                (FileSystemUtil.ROLESET_ENDING)
                || path.endsWith(FileSystemUtil.ROLESET_GRAPH_ENDING)) {
            if (path.contains(plansPath) || path.contains(tasksPath)) {
                return false;
            }
        }
        return true;
    }

    public <T> T parseFile(File modelFile, Class<T> type) throws FileNotFoundException {
        if(!modelFile.exists()){
            throw new FileNotFoundException("The file " + modelFile.getAbsolutePath() + " does not exist!");
        }
        T planElement;
        try {
            while (!modelFile.canRead()) {
                System.out.println("ModelManager: wait to read file: " + modelFile.toString());
                Thread.sleep(1000);
            }
            while (modelFile.length() == 0) {
                Thread.sleep(1000);
            }
            planElement = objectMapper.readValue(modelFile, type);
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            System.err.println("PlanDesigner-ModelManager: Unable to parse " + modelFile);
            System.err.println(e.getMessage());
            return null;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        return planElement;
    }

    public void fireEvent(ModelEvent event) {
        if (event != null) {
            for (IModelEventHandler eventHandler : eventHandlerList) {
                eventHandler.handleModelEvent(event);
            }
        }
    }

    /**
     * Fire an {@link UiExtensionModelEvent} to the {@link IModelEventHandler}.
     *
     * @param event the {@link UiExtensionModelEvent} to fire
     */
    public void fireUiExtensionModelEvent(UiExtensionModelEvent event) {
        if (event != null) {
            for (IModelEventHandler eventHandler : eventHandlerList) {
                eventHandler.handleUiExtensionModelEvent(event);
            }
        }
    }

    /**
     * Update every element that is part of the given {@link PlanModelVisualisationObject}.
     * <p>
     * This method iterates over all {@link PlanElement}s in the given {@link PlanModelVisualisationObject} and calls
     * the fireUiExtensionModelEvent method for every one of them with the coordinates found in their {@link PmlUiExtension}.
     * This is necessary to update the view model when the {@link PlanModelVisualisationObject} has been reloaded.
     *
     * @param planModelVisualisationObject the {@link PlanModelVisualisationObject} to update
     */
    public void updatePlanModelVisualisationObject(PlanModelVisualisationObject planModelVisualisationObject) {
        for (PlanElement planElement : planModelVisualisationObject.getPmlUiExtensionMap().getExtension().keySet()) {
            PmlUiExtension uiExtension = planModelVisualisationObject.getPmlUiExtensionMap().getExtension().get(planElement);
            UiExtensionModelEvent event = new UiExtensionModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, planElement, null);
            event.setExtension(uiExtension);
            fireUiExtensionModelEvent(event);
        }
    }

    /**
     * This methods should only be called through commands.
     *
     * @param type
     * @param newElement
     * @param parentElement
     * @param serializeToDisk
     * @return
     */
    public PlanElement createdPlanElement(String type, PlanElement newElement, PlanElement parentElement, boolean serializeToDisk) {

        if (ignoreModifiedEvent(newElement)) {
            return null;
        }

        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_CREATED, newElement, type);

        switch (type) {
            case Types.PLAN:
            case Types.MASTERPLAN:
                Plan plan = (Plan) newElement;
                planMap.put(plan.getId(), plan);

                //If an PlanModelVisualisationObject exists for a plan with the same id, the plan inside the
                //PlanModelVisualisationObject has to be replaced with the new plan
                PlanModelVisualisationObject visualisation = getCorrespondingPlanModelVisualisationObject(plan.getId());
                if (visualisation != null) {
                    visualisation.setPlan(plan);
                }

                if (serializeToDisk) {
                    serializeToDisk(plan, FileSystemUtil.PLAN_ENDING, true);
                }
                break;
            case Types.ANNOTATEDPLAN:
                // NO-OP
                break;
            case Types.PLANTYPE:
                PlanType planType = (PlanType) newElement;
                planTypeMap.put(planType.getId(), planType);
                if (serializeToDisk) {
                    serializeToDisk(planType, FileSystemUtil.PLANTYPE_ENDING, true);
                }
                break;
            case Types.TASK:
                Task task = (Task) newElement;
                taskRepository.getTasks().add(task);
                task.nameProperty().addListener((observable, oldValue, newValue) -> {
                    taskRepository.setDirty(true);
                });
                task.commentProperty().addListener((observable, oldValue, newValue) -> {
                    taskRepository.setDirty(true);
                });
                task.setTaskRepository(taskRepository);
                break;
            case Types.TASKREPOSITORY:
                taskRepository = (TaskRepository) newElement;
                if (serializeToDisk) {
                    serializeToDisk(taskRepository, FileSystemUtil.TASKREPOSITORY_ENDING, true);
                }
                break;
            case Types.BEHAVIOUR:
                Behaviour behaviour = (Behaviour) newElement;
                behaviourMap.put(behaviour.getId(), behaviour);
                if (serializeToDisk) {
                    serializeToDisk(behaviour, FileSystemUtil.BEHAVIOUR_ENDING, true);
                }
                break;
            case Types.TRANSITION:
                plan = (Plan) parentElement;
                visualisation = getCorrespondingPlanModelVisualisationObject(plan.getId());
                Transition transition = (Transition) newElement;
                visualisation.getPlan().addTransition(transition);
                break;
            case Types.STATE:
            case Types.SUCCESSSTATE:
            case Types.FAILURESTATE:
                plan = (Plan) parentElement;
                visualisation = getCorrespondingPlanModelVisualisationObject(plan.getId());
                visualisation.getPlan().addState((State) newElement);
                UiExtensionModelEvent uiExtensionEvent = new UiExtensionModelEvent(ModelEventType.ELEMENT_CREATED, newElement, type);
                PmlUiExtension extension = visualisation.getPmlUiExtensionMap().getPmlUiExtensionOrCreateNew(newElement);
                uiExtensionEvent.setExtension(extension);
                event = uiExtensionEvent;
                break;
            case Types.ENTRYPOINT:
                plan = (Plan) parentElement;
                visualisation = getCorrespondingPlanModelVisualisationObject(plan.getId());
                visualisation.getPlan().addEntryPoint((EntryPoint) newElement);
//                State entryState = ((EntryPoint) newElement).getState();
//                entryState.setEntryPoint((EntryPoint) newElement);
                HashMap<String, Long> related = new HashMap<>();
                related.put(Types.TASK, ((EntryPoint) newElement).getTask().getId());
                uiExtensionEvent = new UiExtensionModelEvent(ModelEventType.ELEMENT_CREATED, newElement, type);
                uiExtensionEvent.setRelatedObjects(related);
                extension = visualisation.getPmlUiExtensionMap().getPmlUiExtensionOrCreateNew(newElement);
                uiExtensionEvent.setExtension(extension);
                event = uiExtensionEvent;
                break;
            case Types.SYNCHRONIZATION:
                plan = (Plan) parentElement;
                visualisation = getCorrespondingPlanModelVisualisationObject(plan.getId());
                visualisation.getPlan().addSynchronization((Synchronization) newElement);
                UiExtensionModelEvent uiExtensionModelEvent = new UiExtensionModelEvent(ModelEventType.ELEMENT_CREATED, newElement, type);
                extension = visualisation.getPmlUiExtensionMap().getPmlUiExtensionOrCreateNew(newElement);
                uiExtensionModelEvent.setExtension(extension);
                event = uiExtensionModelEvent;
                break;
            case Types.VARIABLE:
                if (parentElement instanceof HasVariables) {
                    ((HasVariables) parentElement).addVariable((Variable)newElement);
                } else {
                    throw new RuntimeException(this.getClass().getName() + ": Parent does not implement WithVariables Interface");
                }
                break;
            default:
                System.err.println("ModelManager: adding or replacing " + type + " not implemented, yet!");
                return null;
        }
        PlanElement oldElement = planElementMap.get(newElement.getId());
        if (oldElement != null) {
            fireEvent(new ModelEvent(ModelEventType.ELEMENT_DELETED, oldElement, type));
        }
        planElementMap.put(newElement.getId(), newElement);
        if (parentElement != null) {
            event.setParentId(parentElement.getId());
        }
        fireEvent(event);
        return oldElement;
    }

    public void removedPlanElement(String type, PlanElement removedElement, PlanElement parentElement, boolean removeFromDisk) {
        switch (type) {
            case Types.PLAN:
            case Types.MASTERPLAN:
                Plan plan = (Plan) removedElement;
                planMap.remove(plan.getId());
                if (removeFromDisk) {
                    removeFromDisk(plan, FileSystemUtil.PLAN_ENDING, true);
                }
                break;
            case Types.ANNOTATEDPLAN:
                //NO-OP
                break;
            case Types.PLANTYPE:
                PlanType planType = (PlanType) removedElement;
                planTypeMap.remove(planType.getId());
                if (removeFromDisk) {
                    removeFromDisk(planType, FileSystemUtil.PLANTYPE_ENDING, true);
                }
                break;
            case Types.TASK:
                Task task = (Task) removedElement;
                taskRepository.getTasks().remove(task);
                task.setTaskRepository(null);
                break;
            case Types.TASKREPOSITORY:
                taskRepository = null;
                if (removeFromDisk) {
                    removeFromDisk((TaskRepository) removedElement, FileSystemUtil.TASKREPOSITORY_ENDING, true);
                }
                break;
            case Types.BEHAVIOUR:
                Behaviour behaviour = (Behaviour) removedElement;
                behaviourMap.remove(behaviour.getId());
                if (removeFromDisk) {
                    removeFromDisk(behaviour, FileSystemUtil.BEHAVIOUR_ENDING, true);
                }
                break;
            case Types.STATE:
            case Types.SUCCESSSTATE:
            case Types.FAILURESTATE:
                //These types only exist inside of plans
                ((Plan) parentElement).removeState((State) removedElement);
                getCorrespondingPlanModelVisualisationObject(parentElement.getId()).getPmlUiExtensionMap().getExtension().remove(removedElement);
                break;
            case Types.ENTRYPOINT:
                ((Plan) parentElement).removeEntryPoint((EntryPoint) removedElement);
                getCorrespondingPlanModelVisualisationObject(parentElement.getId()).getPmlUiExtensionMap().getExtension().remove(removedElement);
                State entryState = ((EntryPoint) removedElement).getState();
                if (entryState != null) {
                    entryState.setEntryPoint(null);
                }
                break;
            case Types.TRANSITION:
                ((Plan) parentElement).removeTransition((Transition) removedElement);
                getCorrespondingPlanModelVisualisationObject(parentElement.getId()).getPmlUiExtensionMap().getExtension().remove(removedElement);
                break;
            case Types.SYNCHRONIZATION:
                ((Plan) parentElement).removeSynchronization((Synchronization) removedElement);
                getCorrespondingPlanModelVisualisationObject(parentElement.getId()).getPmlUiExtensionMap().getExtension().remove(removedElement);
                break;
            case Types.VARIABLE:
                if (parentElement instanceof HasVariables) {
                    ((HasVariables) parentElement).removeVariable((Variable)removedElement);
                } else {
                    throw new RuntimeException(this.getClass().getName() + ": Parent does not implement WithVariables Interface");
                }
                break;
            default:
                System.err.println("ModelManager: Removing " + type + " not implemented, yet!");
        }
        for (IModelEventHandler eventHandler : eventHandlerList) {
            eventHandler.handleCloseTab(removedElement.getId());
        }
        if (parentElement != null) {
            planElementMap.remove(removedElement.getId());

            ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_DELETED, removedElement, type);
            event.setParentId(parentElement.getId());
            fireEvent(event);
        } else {
            System.out.println("Parent is null");
            fireEvent(new ModelEvent(ModelEventType.ELEMENT_DELETED, removedElement, type));
        }
    }

    public void changeAttribute (PlanElement planElement, String elementType, String attribute, Object newValue, Object oldValue) {
        try {
            BeanUtils.setProperty(planElement, attribute, newValue);

            if (attribute.equals("name")) {

                String ending = "";
                switch (elementType) {
                    case Types.PLAN:
                        ending = FileSystemUtil.PLAN_ENDING;
                        break;
                    case Types.PLANTYPE:
                        ending = FileSystemUtil.PLANTYPE_ENDING;
                        break;
                    case Types.BEHAVIOUR:
                        ending = FileSystemUtil.BEHAVIOUR_ENDING;
                        break;
                    case Types.TASKREPOSITORY:
                        ending = FileSystemUtil.TASKREPOSITORY_ENDING;
                        break;
                }

                if (ending != "") {
                    renameFile(getAbsoluteDirectory(planElement), (String) newValue, (String) oldValue, ending);
                    serializeToDisk((SerializablePlanElement) planElement, ending, false);
                }
                ArrayList<PlanElement> usages = getUsages(planElement.getId());
                if (usages != null) {
                    for (PlanElement element : usages) {
                        if (element instanceof Plan) {
                            serializeToDisk((SerializablePlanElement) element, FileSystemUtil.PLAN_ENDING, false);
                        } else if (element instanceof PlanType) {
                            serializeToDisk((SerializablePlanElement) element, FileSystemUtil.PLANTYPE_ENDING, false);
                        } else if (element instanceof Behaviour) {
                            serializeToDisk((SerializablePlanElement) element, FileSystemUtil.BEHAVIOUR_ENDING, false);
                        } else if (element instanceof TaskRepository) {
                            serializeToDisk((SerializablePlanElement) element, FileSystemUtil.TASKREPOSITORY_ENDING, false);
                        }
                    }
                }
            }

        } catch (IllegalAccessException | InvocationTargetException | IOException e) {
            throw new RuntimeException(e);
        }

        ModelEvent event = new ModelEvent(ModelEventType.ELEMENT_ATTRIBUTE_CHANGED, planElement, elementType);
        event.setChangedAttribute(attribute);
        event.setNewValue(newValue);
        fireEvent(event);
    }

    public void moveFile(SerializablePlanElement elementToMove, String newAbsoluteDirectory, String ending) {
        // 1. Delete file from file system
        removeFromDisk(elementToMove, ending, true);

        // 2. Change relative directory property
        elementToMove.setRelativeDirectory(makeRelativeDirectory(newAbsoluteDirectory, elementToMove.getName() + "." + ending));

        // 3. Serialize file to file system
        serializeToDisk(elementToMove, ending, true);

        // 4. Do the 1-3 for the pmlex file in case of pml files
        //TODO implement once pmlex is supported

        // 5. Update external references to file
        serializeEffectedPlanElements(elementToMove);
    }

    private void renameFile(String absoluteDirectory, String newName, String oldName, String ending) throws IOException {
        File oldFile = FileSystemUtil.getFile(absoluteDirectory, oldName, ending);
        File newFile = new File(Paths.get(absoluteDirectory, newName + "." + ending).toString());
        if (newFile.exists()) {
            throw new IOException("ChangeAttributeValue: File " + newFile.toString() + " already exists!");
        }
        if (!oldFile.renameTo(newFile)) {
            throw new IOException("ChangeAttributeValue: Could not rename " + oldFile.toString() + " to " + newFile.toString());
        }
    }

    public ArrayList<PlanElement> getUsages(long modelElementId) {
        ArrayList<PlanElement> usages = new ArrayList<>();

        PlanElement planElement = planElementMap.get(modelElementId);
        if (planElement == null) {
            System.err.println("ModelManager: Usages for unknown plan element (ID: " + modelElementId + ") requested!");
            return null;
        }

        if (planElement instanceof AbstractPlan) {
            usages.addAll(getAbstractPlanUsages(planElement));
        } else if (planElement instanceof Task) {
            usages.addAll(getTaskUsages(planElement));
        } else if (planElement instanceof Variable) {
            usages.addAll(getVariableUsages(planElement));
        } else if (planElement instanceof TaskRepository) {
            usages.addAll(getTaskRepoUsages(planElement));
        }
        else {
            throw new RuntimeException("Usages requested for unhandled elementType of element with id  " + modelElementId);
        }
        return usages;
    }

    private HashSet<Plan> getTaskRepoUsages(PlanElement planElement) {
        HashSet<Plan> usages = new HashSet<>();
        TaskRepository taskRepo = (TaskRepository) planElement;
        for (Plan parent : planMap.values()) {
            for (EntryPoint entryPoint : parent.getEntryPoints()) {
                if (taskRepo.getTasks().contains(entryPoint.getTask())) {
                    usages.add(parent);
                    break;
                }
            }
        }
        return usages;
    }

    private HashSet<Plan> getVariableUsages(PlanElement planElement) {
        HashSet<Plan> usages = new HashSet<>();
        for (Plan parent : planMap.values()) {
            for (Variable var : parent.getVariables()) {
                if (var.getId() == planElement.getId()) {
                    usages.add(parent);
                    break;
                }
            }
            stateLoop:
            for (State state : parent.getStates()) {
                for (Parametrisation param : state.getParametrisations()) {
                    if (param.getSubVariable().getId() == planElement.getId()) {
                        usages.add(parent);
                        break stateLoop;
                    }
                    if (param.getVariable().getId() == planElement.getId()) {
                        usages.add(parent);
                        break stateLoop;
                    }
                }
            }
        }
        return usages;
    }

    private HashSet<Plan> getTaskUsages(PlanElement planElement) {
        HashSet<Plan> usages = new HashSet<>();
        for (Plan parent : planMap.values()) {
            for (EntryPoint entryPoint : parent.getEntryPoints()) {
                if (entryPoint.getTask().getId() == planElement.getId()) {
                    usages.add(parent);
                    break;
                }
            }
        }
        return usages;
    }

    /**
     * Finds AbstractPlans in states and returns the set of parent plans
     * of that states.
     * @param planElement
     * @return Set of plans that hold the relevant states, using the given element
     */
    private HashSet<AbstractPlan> getAbstractPlanUsages(PlanElement planElement) {
        HashSet<AbstractPlan> usages = new HashSet<>();
        for (Plan parentPlan : planMap.values()) {
            stateLoop:
            for (State state : parentPlan.getStates()) {
                for (AbstractPlan child : state.getPlans()) {
                    if (child.getId() == planElement.getId()) {
                        usages.add(parentPlan);
                        break stateLoop;
                    }
                }
            }
        }
        for (PlanType parentPlanType : planTypeMap.values()) {
            for (AnnotatedPlan child : parentPlanType.getPlans()) {
                if (child.getPlan().getId() == planElement.getId()) {
                    usages.add(parentPlanType);
                    break;
                }
            }
        }
        return usages;
    }

    public void handleModelModificationQuery(ModelModificationQuery mmq) {
        AbstractCommand cmd;
        switch (mmq.getQueryType()) {
            case CREATE_ELEMENT:
                switch (mmq.getElementType()) {
                    case Types.PLAN:
                        cmd = new CreatePlan(this, mmq);
                        break;
                    case Types.PLANTYPE:
                        cmd = new CreatePlanType(this, mmq);
                        break;
                    case Types.BEHAVIOUR:
                        cmd = new CreateBehaviour(this, mmq);
                        break;
                    case Types.TASK:
                        cmd = new CreateTask(this, mmq);
                        break;
                    case Types.VARIABLE:
                        cmd = new AddNewVariable(this, mmq);
                        break;
                    default:
                        System.err.println("ModelManager: Creation of unknown model element eventType '" + mmq.getElementType() + "' gets ignored!");
                        return;
                }

                break;
            case PARSE_ELEMENT:
                File f = FileSystemUtil.getFile(mmq);
                if (f != null && ignoreModifiedEvent(getPlanElement(f.toString()))) {
                    return;
                }
                cmd = new ParseAbstractPlan(this, mmq);
                break;
            case DELETE_ELEMENT:
                if (elementDeletedMap.containsKey(mmq.getElementId())) {
                    // TODO change map to list, cause counter is only 1
                    elementDeletedMap.remove(mmq.getElementId());
                    return;
                }
                switch (mmq.getElementType()) {
                    case Types.MASTERPLAN:
                    case Types.PLAN:
                        cmd = new DeletePlan(this, mmq);
                        break;
                    case Types.PLANTYPE:
                        cmd = new DeletePlanType(this, mmq);
                        break;
                    case Types.BEHAVIOUR:
                        cmd = new DeleteBehaviour(this, mmq);
                        break;
                    case Types.TASK:
                        cmd = new DeleteTaskFromRepository(this, mmq);
                        break;
                    case Types.TASKREPOSITORY:
                        cmd = new DeleteTaskRepository(this, mmq);
                        break;
                    case Types.VARIABLE:
                        cmd = new DeleteVariableFromAbstractPlan(this, mmq);
                        break;
                    default:
                        System.err.println("ModelManager: Deletion of unknown model element eventType " + mmq.getElementType() + " gets ignored!");
                        return;
                }
                break;
            case SAVE_ELEMENT:
                cmd = new SerializePlanElement(this, mmq);
                break;
            case ADD_ELEMENT:
                switch (mmq.getElementType()) {
                    case Types.PLAN:
                    case Types.MASTERPLAN:
                        cmd = handlePlanTypeMMQ(mmq);
                        break;
                    case Types.STATE:
                    case Types.SUCCESSSTATE:
                    case Types.FAILURESTATE:
                    case Types.ENTRYPOINT:
                    case Types.PRECONDITION:
                    case Types.RUNTIMECONDITION:
                    case Types.POSTCONDITION:
                    case Types.SYNCHRONIZATION:
                    case Types.INITSTATECONNECTION:
                    case Types.TRANSITION:
                    case Types.BENDPOINT:
                        cmd = handleNewElementInPlanQuery(mmq);
                        break;
                    case Types.SYNCTRANSITION:
                        PlanModelVisualisationObject parentOfElement = getCorrespondingPlanModelVisualisationObject(mmq.getParentId());
                        cmd = new ConnectSynchronizationWithTransition(this, mmq.getElementId(), parentOfElement, mmq.getRelatedObjects().get(Types.TRANSITION));
                        break;
                    default:
                        System.err.println("ModelManager: Unknown model modification query gets ignored!");
                        return;
                }
                break;
            case REMOVE_ELEMENT:
                switch (mmq.getElementType()) {
                    case Types.ANNOTATEDPLAN:
                        cmd = handlePlanTypeMMQ(mmq);
                        break;
                    default:
                        System.err.println("ModelManager: Unknown model modification query gets ignored!");
                        return;
                }
                break;
            case REMOVE_ALL_ELEMENTS:
                switch (mmq.getElementType()) {
                    case Types.PLANTYPE:
                        PlanType planType = planTypeMap.get(mmq.getElementId());
                        cmd = new RemoveAllPlansFromPlanType(this, planType);
                        break;
                    default:
                        System.err.println("ModelManager: Unknown model modification query gets ignored!");
                        return;
                }
                break;
            case RELOAD_ELEMENT:
                switch (mmq.getElementType()) {
                    case Types.PLANTYPE:
                        PlanType planType = planTypeMap.get(mmq.getElementId());
                        mmq.absoluteDirectory = Paths.get(plansPath, planType.getRelativeDirectory()).toString();
                        mmq.name = planType.getName();
                        cmd = new ParseAbstractPlan(this, mmq);
                        break;
                    case Types.PLAN:
                    case Types.MASTERPLAN:
                        Plan plan = planMap.get(mmq.getElementId());
                        mmq.absoluteDirectory = Paths.get(plansPath, plan.getRelativeDirectory()).toString();
                        mmq.name = plan.getName();
                        cmd = new ParseAbstractPlan(this, mmq);
                        break;
                    default:
                        System.err.println("ModelManager: Unknown model modification query gets ignored!");
                        return;
                }
                break;
            case CHANGE_ELEMENT:
                switch (mmq.getElementType()) {
                    case Types.SYNCTRANSITION: {
                        PlanModelVisualisationObject parenOfElement = getCorrespondingPlanModelVisualisationObject(mmq.getParentId());
                        cmd = new ConnectSynchronizationWithTransition(this, mmq.getElementId(), parenOfElement, (Long) mmq.getNewValue());
                    } break;
                    default:
                        // TODO: Make this a switch case command, like everywhere else in this method, too!
                        cmd = new ChangeAttributeValue(this, mmq);
                        break;
                } break;
            case MOVE_ELEMENT:
                cmd = new MoveFile(this, mmq);
                break;
            default:
                System.err.println("ModelManager: Unknown model modification query gets ignored!");
                return;
        }
        commandStack.storeAndExecute(cmd);
    }

    /**
     * Creates a path relative to the plansPath
     *
     * @param absoluteDirectory
     * @param fileName
     * @return
     */
    public String makeRelativeDirectory(String absoluteDirectory, String fileName) {
        String relativeDirectory = absoluteDirectory.replace(plansPath, "");
        relativeDirectory = relativeDirectory.replace(tasksPath, "");
        relativeDirectory = relativeDirectory.replace(rolesPath, "");
        relativeDirectory = relativeDirectory.replace(fileName, "");
        if (relativeDirectory.startsWith(File.separator)) {
            relativeDirectory = relativeDirectory.substring(1);
        }
        if (relativeDirectory.endsWith(File.separator)) {
            relativeDirectory = relativeDirectory.substring(0, relativeDirectory.length() - 1);
        }
        return relativeDirectory;
    }

    /**
     * This method is just for the SerializePlanElement Command. Nobody else should call it ... :P
     * @param planElement
     * @param type
     */
    public void serialize(SerializablePlanElement planElement, String type) {
        switch (type) {
            case Types.TASK:
            case Types.TASKREPOSITORY:
                serializeToDisk(planElement, FileSystemUtil.TASKREPOSITORY_ENDING, false);
                break;
            case Types.PLANTYPE:
                serializeToDisk(planElement, FileSystemUtil.PLANTYPE_ENDING, false);
                break;
            case Types.PLAN:
            case Types.MASTERPLAN:
                serializeToDisk(planElement, FileSystemUtil.PLAN_ENDING, false);
                break;
            case Types.BEHAVIOUR:
                serializeToDisk(planElement, FileSystemUtil.BEHAVIOUR_ENDING, false);
                break;
            default:
                System.err.println("ModelManager: Serialization of type " + type + " not implemented, yet!");
                break;
        }
    }

    /**
     * Serializes an SerializablePlanElement to disk.
     *
     * @param planElement
     */
    private void serializeToDisk(SerializablePlanElement planElement, String ending, boolean movedOrCreated) {
        try {

            // Setting the values in the elementsSaved map at the beginning,
            // because otherwise listeners may react before values are updated
            if (!movedOrCreated) {
                // the counter is set to 2 because, saving an element always creates two filesystem modified events
                int counter = 2;
                // when a plan is saved it needs to be 4 however, because the extension is saved as well
                if (ending.equals(FileSystemUtil.PLAN_ENDING)) {
                    counter = 4;
                }
                elementsSavedMap.put(planElement.getId(), counter);
            }
            File outfile = Paths.get(plansPath, planElement.getRelativeDirectory(), planElement.getName() + "." + ending).toFile();
            if (ending.equals(FileSystemUtil.PLAN_ENDING)) {
                objectMapper.writeValue(outfile, (Plan) planElement);
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_SERIALIZED, planElement, Types.PLAN));

                //Save the corresponding PlanModelVisualisationObject
                PlanModelVisualisationObject planModelVisualisationObject = planModelVisualisationObjectMap.get(planElement.getId());
                if (planModelVisualisationObject != null) {
                    File visualisationFile = Paths.get(plansPath, planElement.getRelativeDirectory()
                            , planElement.getName() + "." + FileSystemUtil.PLAN_EXTENSION_ENDING).toFile();
                    objectMapper.writeValue(visualisationFile, planModelVisualisationObject);
                }

            } else if (ending.equals(FileSystemUtil.PLANTYPE_ENDING)) {
                objectMapper.writeValue(outfile, (PlanType) planElement);
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_SERIALIZED, planElement, Types.PLANTYPE));
            } else if (ending.equals(FileSystemUtil.BEHAVIOUR_ENDING)) {
                objectMapper.writeValue(outfile, (Behaviour) planElement);
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_SERIALIZED, planElement, Types.BEHAVIOUR));
            } else if (ending.equals(FileSystemUtil.TASKREPOSITORY_ENDING)) {
                outfile = Paths.get(tasksPath, planElement.getRelativeDirectory(), planElement.getName() + "." + ending).toFile();
                objectMapper.writeValue(outfile, (TaskRepository) planElement);
                fireEvent(new ModelEvent(ModelEventType.ELEMENT_SERIALIZED, planElement, Types.TASKREPOSITORY));
            } else {
                throw new RuntimeException("Modelmanager: Trying to serialize a file with unknown ending: " + ending);
            }
            planElement.setDirty(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes AbstractPlan from disk.
     *
     * @param planElement
     */
    public void removeFromDisk(SerializablePlanElement planElement, String ending, boolean doneByGUI) {
        if (doneByGUI) {
            elementDeletedMap.put(planElement.getId(), 1);
        }
        File outfile = Paths.get(plansPath, planElement.getRelativeDirectory(), planElement.getName() + "." + ending).toFile();
        outfile.delete();
    }

    public AbstractCommand handlePlanTypeMMQ(ModelModificationQuery mmq) {
        PlanElement parent = planElementMap.get(mmq.getParentId());
        if (parent == null || !(parent instanceof PlanType)
                || (mmq.getQueryType() != ModelQueryType.ADD_ELEMENT && mmq.getQueryType() != ModelQueryType.REMOVE_ELEMENT)) {
            return null;
        }

        if (mmq.getQueryType() == ModelQueryType.ADD_ELEMENT) {
            return new AddPlanToPlanType(this, mmq);
        } else if (mmq.getQueryType() == ModelQueryType.REMOVE_ELEMENT) {
            return new RemovePlanFromPlanType(this, mmq);
        }

        return null;
    }

    private AbstractCommand handleNewElementInPlanQuery(ModelModificationQuery mmq) {
        PlanModelVisualisationObject parenOfElement = getCorrespondingPlanModelVisualisationObject(mmq.getParentId());
        AbstractCommand cmd;
        int x = 0;
        int y = 0;
        if (mmq instanceof UiExtensionModelModificationQuery) {
            x = ((UiExtensionModelModificationQuery) mmq).getNewX();
            y = ((UiExtensionModelModificationQuery) mmq).getNewY();
        }

        switch (mmq.elementType) {
            case Types.STATE:
                //Creating a new State and setting all necessary fields
                State state = new State();
                state.setParentPlan(parenOfElement.getPlan());
                state.setName(mmq.name);
                state.setComment(mmq.getComment());
//                //Putting the created state in the planElementMap so that it can be found there later
//                planElementMap.put(state.getId(), state);
                //Creating an extension with coordinates
                PmlUiExtension extension = new PmlUiExtension();
                extension.setX(x);
                extension.setY(y);
                //create an command, that inserts the created State in the Plan
                cmd = new AddStateInPlan(this, parenOfElement, state, extension, Types.STATE);
                break;
            case Types.SUCCESSSTATE:
            case Types.FAILURESTATE:
                TerminalState terminalState = new TerminalState(mmq.elementType == Types.SUCCESSSTATE, null);
                terminalState.setParentPlan(parenOfElement.getPlan());
                terminalState.setName(mmq.name);
                terminalState.setComment(mmq.getComment());
                PmlUiExtension terminalExtension = new PmlUiExtension();
                terminalExtension.setX(x);
                terminalExtension.setY(y);
                cmd = new AddStateInPlan(this, parenOfElement, terminalState, terminalExtension, Types.SUCCESSSTATE);
                break;
            case Types.TRANSITION:
                State in = (State) getPlanElement(mmq.getRelatedObjects().get(Transition.INSTATE));
                State out = (State) getPlanElement(mmq.getRelatedObjects().get(Transition.OUTSTATE));
                cmd = new AddTransitionInPlan(this, parenOfElement, in, out);
                break;
            case Types.INITSTATECONNECTION:
                EntryPoint inEntryPoint = (EntryPoint) getPlanElement((mmq.getRelatedObjects().get(State.ENTRYPOINT)));
                State outState = (State) getPlanElement(mmq.getRelatedObjects().get(EntryPoint.STATE));
                cmd = new ConnectEntryPointsWithState(this, parenOfElement, inEntryPoint, outState);
                break;
            case Types.ENTRYPOINT:
                EntryPoint entryPoint = new EntryPoint();
                entryPoint.setPlan(parenOfElement.getPlan());
                PmlUiExtension entryPointExtension = new PmlUiExtension();
                entryPointExtension.setX(x);
                entryPointExtension.setY(y);
                entryPoint.setTask((Task) getPlanElement(mmq.getRelatedObjects().get(Types.TASK)));
//                entryPoint.setState((State) getPlanElement(mmq.getRelatedObjects().get(Types.STATE)));
                cmd = new AddEntryPointInPlan(this, parenOfElement, entryPoint, entryPointExtension);
                break;
            case Types.BENDPOINT:
                BendPoint bendPoint = new BendPoint();
                bendPoint.setX(x);
                bendPoint.setY(y);
                Transition transition = (Transition) getPlanElement(mmq.getRelatedObjects().get(Types.TRANSITION));
                bendPoint.setTransition(transition);
                PlanModelVisualisationObject modelObject = getCorrespondingPlanModelVisualisationObject(mmq.getParentId());
                extension = modelObject.getPmlUiExtensionMap().getPmlUiExtensionOrCreateNew(transition);
                cmd = new AddBendpointToPlan(this, parenOfElement, bendPoint, extension);
                break;
            case Types.SYNCHRONIZATION:
                Synchronization sync = new Synchronization();
                sync.setName(mmq.name);
                sync.setComment(mmq.getComment());
                sync.setSyncedTransitions(new ArrayList<>());
                extension = new PmlUiExtension();
                extension.setX(x);
                extension.setY(y);
                cmd = new AddSynchronizationToPlan(this, sync, parenOfElement, extension);
                break;
            case Types.PRECONDITION:
            case Types.RUNTIMECONDITION:
            case Types.POSTCONDITION:
            case Types.SYNCTRANSITION:
                //TODO: Create commands for the other types
            default:
                System.err.println("ModelManger: Unknown type to add to plan gets ignored! Type was: " + mmq.elementType);
                return null;
        }

        return cmd;
    }

    public TaskRepository getTaskRepository() {
        return taskRepository;
    }

    public String getAbsoluteDirectory(PlanElement element) {
        if (element instanceof Plan || element instanceof PlanType || element instanceof Behaviour) {
            return Paths.get(plansPath, ((SerializablePlanElement) element).getRelativeDirectory()).toString();
        }
        if (element instanceof TaskRepository) {
            return Paths.get(tasksPath, ((SerializablePlanElement) element).getRelativeDirectory()).toString();
        }
        return null;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof CommandStack) {
            CommandStack cmd = (CommandStack) o;
            for (IModelEventHandler modelEventHandler : eventHandlerList) {
                modelEventHandler.disableRedo(!cmd.isRedoPossible());
                modelEventHandler.disableUndo(!cmd.isUndoPossible());
            }
        }
    }

    public void undo() {
        commandStack.undo();
    }

    public void redo() {
        commandStack.redo();
    }

    /**
     * Method to handle changes concerning the UiExtensionModel.
     * <p>
     * Similarly to the handleModelModificationQuery-method this Method receives a query containing the information
     * about the changes to the model, which is in this case the UiExtensionModel, and creates an {@link AbstractCommand}
     * to execute these changes. Because this method is only called, when an object was moved (changed its position) in
     * the ui, it will always create a {@link ChangePosition}-command.
     *
     * @param uimmq query containing information about a change in the UiExtensionModel
     */
    public void handleUiExtensionModelModificationQuery(UiExtensionModelModificationQuery uimmq) {
        PlanElement planElement = getPlanElement(uimmq.getElementId());
        PlanModelVisualisationObject planModelVisualisationObject = getCorrespondingPlanModelVisualisationObject(uimmq.getParentId());
        PmlUiExtension uiExtension = planModelVisualisationObject.getPmlUiExtensionMap().getPmlUiExtensionOrCreateNew(planElement);

        AbstractCommand cmd = new ChangePosition(this, uiExtension, planElement, uimmq.getNewX(), uimmq.getNewY());
        commandStack.storeAndExecute(cmd);
    }

    /**
     * Finding a {@link PlanModelVisualisationObject} by the id of its {@link Plan}.
     * <p>
     * If no such {@link PlanModelVisualisationObject} exits, a new one is created and stored.
     *
     * @param id the id of the {@link Plan} a {@link PlanModelVisualisationObject} is required for
     * @return the {@link PlanModelVisualisationObject} corresponding to the given id
     */
    public PlanModelVisualisationObject getCorrespondingPlanModelVisualisationObject(long id) {
        PlanModelVisualisationObject pmvo = planModelVisualisationObjectMap.get(id);
        if (pmvo == null) {
            Plan plan = planMap.get(id);
            if (plan == null) {
                LOG.error("Cannot create PlanModelVisualisationObject, because no plan with id " + id + "exists");
                return null;
            }
            PmlUiExtensionMap pmlUiExtensionMap = new PmlUiExtensionMap();
            pmvo = new PlanModelVisualisationObject(plan, pmlUiExtensionMap);
            planModelVisualisationObjectMap.put(id, pmvo);
        }
        return pmvo;
    }

    /**
     * Serializes all Elements that could be effected by moving the given PlanElement.
     * @param movedElement
     */
    private void serializeEffectedPlanElements(PlanElement movedElement) {
        ArrayList<PlanElement> usages = getUsages(movedElement.getId());
        for (PlanElement planElement : usages) {
            if (planElement instanceof SerializablePlanElement) {
                SerializablePlanElement serializablePlanElement = (SerializablePlanElement) planElement;
                serializeToDisk(serializablePlanElement, FileSystemUtil.getFileEnding(serializablePlanElement), true);
            }
        }
    }

    /**
     * Check, whether to ignore the modification of the given {@link PlanElement}
     *
     * @param newElement the {@link PlanElement} to check
     * @return true, if should be ignored
     */
    private boolean ignoreModifiedEvent(PlanElement newElement) {
        if (elementsSavedMap.containsKey(newElement.getId())) {
            int counter = elementsSavedMap.get(newElement.getId()) - 1;
            if (counter == 0) {
                // second event arrived, so remove the entry
                elementsSavedMap.remove(newElement.getId());
            } else {
                // first event arrived, so set the reduced counter
                elementsSavedMap.put(newElement.getId(), counter);
            }
            return true;
        }
        return false;
    }


}
