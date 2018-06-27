package de.uni_kassel.vs.cn.planDesigner.controller;

import de.uni_kassel.vs.cn.generator.GeneratedSourcesManager;
import de.uni_kassel.vs.cn.planDesigner.alicamodel.*;
import de.uni_kassel.vs.cn.planDesigner.command.CommandStack;
import de.uni_kassel.vs.cn.planDesigner.configuration.Configuration;
import de.uni_kassel.vs.cn.planDesigner.configuration.ConfigurationEventHandler;
import de.uni_kassel.vs.cn.planDesigner.configuration.ConfigurationManager;
import de.uni_kassel.vs.cn.planDesigner.events.ModelOperationType;
import de.uni_kassel.vs.cn.planDesigner.events.ResourceCreationEvent;
import de.uni_kassel.vs.cn.planDesigner.filebrowser.FileSystemEventHandler;
import de.uni_kassel.vs.cn.planDesigner.handlerinterfaces.IGuiStatusHandler;
import de.uni_kassel.vs.cn.planDesigner.handlerinterfaces.IMoveFileHandler;
import de.uni_kassel.vs.cn.planDesigner.handlerinterfaces.IResourceCreationHandler;
import de.uni_kassel.vs.cn.planDesigner.events.IModelEventHandler;
import de.uni_kassel.vs.cn.planDesigner.events.ModelEvent;
import de.uni_kassel.vs.cn.planDesigner.modelmanagement.FileSystemUtil;
import de.uni_kassel.vs.cn.planDesigner.modelmanagement.ModelManager;
import de.uni_kassel.vs.cn.planDesigner.modelmanagement.ModelModificationQuery;
import de.uni_kassel.vs.cn.planDesigner.view.I18NRepo;
import de.uni_kassel.vs.cn.planDesigner.view.filebrowser.FileTreeView;
import de.uni_kassel.vs.cn.planDesigner.view.filebrowser.TreeViewModelElement;
import de.uni_kassel.vs.cn.planDesigner.handlerinterfaces.IShowUsageHandler;
import de.uni_kassel.vs.cn.planDesigner.view.repo.RepositoryTabPane;
import de.uni_kassel.vs.cn.planDesigner.view.repo.RepositoryViewModel;
import de.uni_kassel.vs.cn.planDesigner.view.repo.ViewModelElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;

/**
 * Central class that synchronizes model and view.
 * It is THE CONTROLLER regarding the Model-View-Controller pattern,
 * implemented in the Plan Designer.
 */
public final class Controller implements IModelEventHandler, IShowUsageHandler, IGuiStatusHandler, IResourceCreationHandler, IMoveFileHandler {

    // Common Objects
    private ConfigurationManager configurationManager;
    private FileSystemEventHandler fileSystemEventHandler;
    private ConfigurationEventHandler configEventHandler;
    private I18NRepo i18NRepo;

    // Model Objects
    private ModelManager modelManager;
    private CommandStack commandStack;

    // View Objects
    private RepositoryViewModel repoViewModel;
    private MainWindowController mainWindowController;
    private ConfigurationWindowController configWindowController;
    private RepositoryTabPane repoTabPane;

    // Code Generation Objects
    GeneratedSourcesManager generatedSourcesManager;

    public Controller() {
        configurationManager = ConfigurationManager.getInstance();
        configurationManager.setController(this);

        i18NRepo = I18NRepo.getInstance();

        mainWindowController = MainWindowController.getInstance();
        mainWindowController.setGuiStatusHandler(this);
        mainWindowController.setResourceCreationHandler(this);
        mainWindowController.setShowUsageHandler(this);
        mainWindowController.setMoveFileHandler(this);

        commandStack = new CommandStack();

        setupConfigGuiStuff();

        repoTabPane = mainWindowController.getRepositoryTabPane();

        repoViewModel = new RepositoryViewModel();
        repoViewModel.setRepositoryTabPane(repoTabPane);

        fileSystemEventHandler = new FileSystemEventHandler(this);
        new Thread(fileSystemEventHandler).start(); // <- will be stopped by the PlanDesigner.isRunning() flag

        setupModelManager();
        setupGeneratedSourcesManager();
    }

    protected void setupGeneratedSourcesManager() {
        generatedSourcesManager = new GeneratedSourcesManager();
        generatedSourcesManager.setEditorExecutablePath(configurationManager.getEditorExecutablePath());
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        if (activeConfiguration != null) {
            generatedSourcesManager.setGenSrcPath(activeConfiguration.getGenSrcPath());
        }
    }

    protected void setupModelManager() {
        modelManager = new ModelManager();
        modelManager.addListener(this);
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        if (activeConfiguration != null) {
            modelManager.setPlansPath(activeConfiguration.getPlansPath());
            modelManager.setTasksPath(activeConfiguration.getTasksPath());
            modelManager.setRolesPath(activeConfiguration.getRolesPath());
        }
    }

    protected void setupConfigGuiStuff() {
        configWindowController = new ConfigurationWindowController();
        configEventHandler = new ConfigurationEventHandler(configWindowController, configurationManager);
        configWindowController.setHandler(configEventHandler);
        mainWindowController.setConfigWindowController(configWindowController);
    }

    // Handler Event Methods


    /**
     * Handles events fired by the model manager, when the model has changed.
     *
     * @param event Object that describes the purpose/context of the fired event.
     */
    public void handleModelEvent(ModelEvent event) {
        if (repoViewModel == null) {
            return;
        }

        PlanElement planElement = event.getNewElement();
        String type = FileSystemUtil.getTypeString(planElement);
        switch (event.getType()) {
            case ELEMENT_CREATED:
                if (planElement instanceof Plan) {
                    Plan plan = (Plan) planElement;
                    addTreeViewElement(plan, type);
                    repoViewModel.addPlan(new ViewModelElement(plan.getId(), plan.getName(), type));
                } else if (planElement instanceof PlanType) {
                    addTreeViewElement((AbstractPlan) planElement, type);
                    repoViewModel.addPlanType(new ViewModelElement(planElement.getId(), planElement.getName(), type));
                } else if (planElement instanceof Behaviour) {
                    addTreeViewElement((AbstractPlan) planElement, type);
                    repoViewModel.addBehaviour(new ViewModelElement(planElement.getId(), planElement.getName(), type));
                } else if (planElement instanceof TaskRepository) {
                    addTreeViewElement((AbstractPlan) planElement, type);
                    for (Task task : ((TaskRepository) planElement).getTasks()) {
                        type = FileSystemUtil.getTypeString(task);
                        repoViewModel.addTask(new ViewModelElement(task.getId(), task.getName(), type));
                    }
                }
                break;
            case ELEMENT_DELETED:
                System.out.println("Controller: ELEMENT_DELETED not implemented yet!");
                removeTreeViewElement((AbstractPlan) planElement, type);
                repoViewModel.removePlanElement(new ViewModelElement(planElement.getId(), planElement.getName(), type));
                break;
            case ELEMENT_ATTRIBUTE_CHANGED:
                throw new RuntimeException("Not implemented, yet!");
            default:
                throw new RuntimeException("Unknown model event captured!");
        }
    }

    private void addTreeViewElement(AbstractPlan planElement, String type) {
        FileTreeView fileTreeView = mainWindowController.getFileTreeView();
        if (fileTreeView != null) {
            mainWindowController.getFileTreeView().addTreeViewModelElement(new TreeViewModelElement(planElement.getId(),
                    planElement.getName(), type, planElement.getRelativeDirectory()));
        }
    }

    private void removeTreeViewElement(AbstractPlan planElement, String type) {
        FileTreeView fileTreeView = mainWindowController.getFileTreeView();
        if (fileTreeView != null) {
            mainWindowController.getFileTreeView().removeTreeViewModelElement(new TreeViewModelElement(planElement.getId(),
                    planElement.getName(), type, planElement.getRelativeDirectory()));
        }
    }

    /**
     * Called by the 'ShowUsage'-ContextMenu of RepositoryHBoxes
     *
     * @param viewModelElement
     * @return
     */
    @Override
    public ArrayList<ViewModelElement> getUsages(ViewModelElement viewModelElement) {
        ArrayList<ViewModelElement> usageViewModelElements = new ArrayList<>();
        ArrayList<PlanElement> usagePlanElements = this.modelManager.getUsages(viewModelElement.getId());
        if (usagePlanElements != null) {
            for (PlanElement planElement : usagePlanElements) {
                usageViewModelElements.add(new ViewModelElement(planElement.getId(), planElement.getName(), FileSystemUtil.getTypeString(planElement)));
            }
        }
        return usageViewModelElements;
    }

    /**
     * Called by the configuration manager, if the active configuration has changed.
     */
    public void handleConfigurationChanged() {
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        mainWindowController.setUpFileTreeView(activeConfiguration.getPlansPath(), activeConfiguration.getRolesPath(), activeConfiguration.getTasksPath());
    }

    /**
     * Called by the main window controlled at the end of its initialized method.
     */
    @Override
    public void handleGuiInitialized() {
        mainWindowController.enableMenuBar();
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        if (activeConfiguration != null) {
            mainWindowController.setUpFileTreeView(activeConfiguration.getPlansPath(), activeConfiguration.getRolesPath(), activeConfiguration.getTasksPath());
            new Thread(fileSystemEventHandler).start(); // <- will be stopped by the PlanDesigner.isRunning() flag
            modelManager.loadModelFromDisk();
        }
        repoTabPane = mainWindowController.getRepositoryTabPane();
        repoViewModel.setRepositoryTabPane(repoTabPane);
        repoViewModel.initGuiContent();
    }

    /**
     * Called by the context menu for creating plans, behaviours etc.
     *
     * @param event
     */
    @Override
    public void handleResourceCreationEvent(ResourceCreationEvent event) {
        ModelModificationQuery mmq = new ModelModificationQuery(ModelOperationType.CREATE_ELEMENT, event.getAbsoluteDirectory(), event.getType(), event.getName());
        this.modelManager.handleModelModificationQuery(mmq);
    }

    /**
     * Called when something relevant in the filesystem has changed.
     *
     * @param event
     * @param path
     */
    public void handleFileSystemEvent(WatchEvent event, Path path) {
        WatchEvent.Kind kind = event.kind();
        ModelModificationQuery mmq;
        if (kind.equals((StandardWatchEventKinds.ENTRY_MODIFY))) {
            mmq = new ModelModificationQuery(ModelOperationType.PARSE_ELEMENT, path.toString());
        } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            mmq = new ModelModificationQuery(ModelOperationType.DELETE_ELEMENT, path.toString());
        } else {
            System.err.println("Controller: Unknown filesystem event type received that gets ignored!");
            return;
        }
        modelManager.handleModelModificationQuery(mmq);
    }

    /**
     * Called by the FileTreeView when moving files
     */
    public void moveFile(long id, Path originalPath, Path newPath) {
        modelManager.moveFile(id, originalPath, newPath);
    }
}
