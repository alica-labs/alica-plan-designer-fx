package de.unikassel.vs.alica.planDesigner.controller;

import de.unikassel.vs.alica.generator.Codegenerator;
import de.unikassel.vs.alica.generator.GeneratedSourcesManager;
import de.unikassel.vs.alica.generator.plugin.PluginManager;
import de.unikassel.vs.alica.planDesigner.ViewModelManagement.ViewModelManager;
import de.unikassel.vs.alica.planDesigner.alicaConfiguration.AlicaConfigurationEventHandler;
import de.unikassel.vs.alica.planDesigner.alicaConfiguration.AlicaConfigurationManager;
import de.unikassel.vs.alica.planDesigner.alicamodel.*;
import de.unikassel.vs.alica.planDesigner.configuration.Configuration;
import de.unikassel.vs.alica.planDesigner.configuration.ConfigurationEventHandler;
import de.unikassel.vs.alica.planDesigner.configuration.ConfigurationManager;
import de.unikassel.vs.alica.planDesigner.converter.CustomLongConverter;
import de.unikassel.vs.alica.planDesigner.converter.CustomPlanElementConverter;
import de.unikassel.vs.alica.planDesigner.converter.CustomStringConverter;
import de.unikassel.vs.alica.planDesigner.events.*;
import de.unikassel.vs.alica.planDesigner.filebrowser.FileSystemEventHandler;
import de.unikassel.vs.alica.planDesigner.globalsConfiguration.GlobalsConfEventHandler;
import de.unikassel.vs.alica.planDesigner.globalsConfiguration.GlobalsConfManager;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IAlicaHandler;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiModificationHandler;
import de.unikassel.vs.alica.planDesigner.handlerinterfaces.IGuiStatusHandler;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelManager;
import de.unikassel.vs.alica.planDesigner.modelmanagement.ModelModificationQuery;
import de.unikassel.vs.alica.planDesigner.plugin.PluginEventHandler;
import de.unikassel.vs.alica.planDesigner.uiextensionmodel.BendPoint;
import de.unikassel.vs.alica.planDesigner.view.I18NRepo;
import de.unikassel.vs.alica.planDesigner.view.Types;
import de.unikassel.vs.alica.planDesigner.view.editor.container.StateContainer;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.AbstractPlanTab;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.EditorTabPane;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.planTab.PlanTab;
import de.unikassel.vs.alica.planDesigner.view.editor.tab.taskRepoTab.TaskRepositoryTab;
import de.unikassel.vs.alica.planDesigner.view.menu.FileTreeViewContextMenu;
import de.unikassel.vs.alica.planDesigner.view.model.*;
import de.unikassel.vs.alica.planDesigner.view.repo.RepositoryTabPane;
import de.unikassel.vs.alica.planDesigner.view.repo.RepositoryViewModel;
import de.unikassel.vs.pdDebug.Protocol;
import de.unikassel.vs.pdDebug.Subscriber;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Central class that synchronizes model and view.
 * It is THE CONTROLLER regarding the Model-View-Controller pattern,
 * implemented in the Plan Designer.
 */
public final class Controller implements IModelEventHandler, IGuiStatusHandler, IGuiModificationHandler, IAlicaHandler {

    // Common Objects
    private ConfigurationManager configurationManager;
    private AlicaConfigurationManager alicaConfigurationManager;
    private GlobalsConfManager globalsConfManager;
    private FileSystemEventHandler fileSystemEventHandler;
    private ConfigurationEventHandler configEventHandler;
    private AlicaConfigurationEventHandler alicaConfigurationEventHandler;
    private GlobalsConfEventHandler globalsConfEventHandler;
    private PluginManager pluginManager;
    private PluginEventHandler pluginEventHandler;

    // Model Objects
    private ModelManager modelManager;

    // View Objects
    private RepositoryViewModel repoViewModel;
    private MainWindowController mainWindowController;
    private ConfigurationWindowController configWindowController;
    private AlicaConfWindowController alicaConfWindowController;
    private GlobalsConfWindowController globalsConfWindowController;
    private RepositoryTabPane repoTabPane;
    private EditorTabPane editorTabPane;
    private ViewModelManager viewModelManager;

    // Code Generation Objects
    private GeneratedSourcesManager generatedSourcesManager;

    public Controller() {
        configurationManager = ConfigurationManager.getInstance();
        configurationManager.setController(this);

        alicaConfigurationManager = AlicaConfigurationManager.getInstance();
        alicaConfigurationManager.setController(this);

        globalsConfManager = GlobalsConfManager.getInstance();
        globalsConfManager.setController(this);

        pluginManager = PluginManager.getInstance();

        mainWindowController = MainWindowController.getInstance();
        mainWindowController.setGuiStatusHandler(this);
        mainWindowController.setGuiModificationHandler(this);
        mainWindowController.setAlicaHandler(this);

        setupConfigGuiStuff();
        setupAlicaConfGuiStuff();
        setupGlobalsConfGuiStuff();

        fileSystemEventHandler = new FileSystemEventHandler(this);
        new Thread(fileSystemEventHandler).start(); // <- will be stopped by the PlanDesigner.isRunning() flag

        setupModelManager();
        setupGeneratedSourcesManager();

        viewModelManager = new ViewModelManager(modelManager, this);

        repoViewModel = viewModelManager.createRepositoryViewModel();

        setupBeanConverters();
    }

    protected void setupGeneratedSourcesManager() {
        generatedSourcesManager = new GeneratedSourcesManager();
        generatedSourcesManager.setEditorExecutablePath(configurationManager.getEditorExecutablePath());
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        if (activeConfiguration != null) {
            generatedSourcesManager.setCodegenPath(activeConfiguration.getGenSrcPath());
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
    protected void setupGlobalsConfGuiStuff() {
        globalsConfWindowController = new GlobalsConfWindowController(0 ,"", "", "");

        globalsConfEventHandler = new GlobalsConfEventHandler(globalsConfWindowController, globalsConfManager);
        globalsConfWindowController.setHandler(globalsConfEventHandler);
        globalsConfWindowController.loadDefaultGlobalsConfNoGui();

        mainWindowController.setGlobalsConfWindowController(globalsConfWindowController);
    }
    protected void setupAlicaConfGuiStuff() {
        alicaConfWindowController = new AlicaConfWindowController();

        alicaConfigurationEventHandler = new AlicaConfigurationEventHandler(alicaConfWindowController,alicaConfigurationManager);
        alicaConfWindowController.setHandler(alicaConfigurationEventHandler);
        alicaConfWindowController.loadDefaultAlicaConfNoGui();

        mainWindowController.setAlicaConfWindowController(alicaConfWindowController);
    }
    protected void setupConfigGuiStuff() {
        configWindowController = new ConfigurationWindowController();

        configEventHandler = new ConfigurationEventHandler(configWindowController, configurationManager);
        configWindowController.setHandler(configEventHandler);

        pluginEventHandler = new PluginEventHandler(configWindowController, pluginManager);
        configWindowController.setPluginEventHandler(pluginEventHandler);

        mainWindowController.setConfigWindowController(configWindowController);
    }

    private void setupBeanConverters() {
        CustomStringConverter customStringConverter = new CustomStringConverter();
        CustomLongConverter customLongConverter = new CustomLongConverter();
        CustomPlanElementConverter customPlanElementConverter = new CustomPlanElementConverter(this.modelManager);

        // Temporarily setting the log-level to prevent unnecessary output
        Level logLevel = Logger.getRootLogger().getLevel();
        Logger.getRootLogger().setLevel(Level.WARN);

        ConvertUtils.register(customStringConverter, String.class);
        ConvertUtils.register(customLongConverter, Long.class);
        ConvertUtils.register(customPlanElementConverter, PlanElement.class);

        // Setting the log-level to its previous level
        Logger.getRootLogger().setLevel(logLevel);
    }

    // HANDLER EVENT METHODS

    /**
     * Handles Codegeneration events
     *
     * @param event
     */
    public void generateCode(GuiModificationEvent event, Text generatingText) {
        Codegenerator codegenerator = new Codegenerator(
                modelManager.getPlans(),
                modelManager.getBehaviours(),
                modelManager.getConditions(),
                configurationManager.getClangFormatPath(),
                generatedSourcesManager);
        Platform.runLater(() -> generatingText.textProperty().bind(codegenerator.currentFile));
        switch (event.getEventType()) {
            case GENERATE_ELEMENT:
                codegenerator.generate((AbstractPlan) modelManager.getPlanElement(event.getElementId()));
                break;
            case GENERATE_ALL_ELEMENTS:
                codegenerator.generate();
                break;
            default:
                System.out.println("Controller.generateCode(): Event type " + event.getEventType() + " is not handled.");
                break;
        }
    }

    /**
     * Handles events fired by the model manager, when the model has changed.
     *
     * @param event Object that describes the purpose/context of the fired event.
     */
    public void handleModelEvent(ModelEvent event) {
        if(event.getEventType().equals(ModelEventType.ELEMENT_FOLDER_DELETED)){
            updateFileTreeView(event, null);
            return;
        }
        PlanElement modelElement = event.getElement();
        ViewModelElement viewModelElement = viewModelManager.getViewModelElement(modelElement);

        switch (event.getElementType()) {
            case Types.MASTERPLAN:
            case Types.PLAN:
            case Types.PLANTYPE:
            case Types.BEHAVIOUR:
            case Types.ROLESET:
            case Types.TASKREPOSITORY:
                updateRepos(event.getEventType(), viewModelElement);
                updateFileTreeView(event, viewModelElement);
                break;
            case Types.TASK:
                updateRepos(event.getEventType(), viewModelElement);
                break;
        }
        // Generate files for moved code
        if(event.getEventType() == ModelEventType.ELEMENT_ATTRIBUTE_CHANGED  && "relativeDirectory".equals(event.getChangedAttribute())) {
            mainWindowController.waitOnProgressLabel(() -> generateCode(new GuiModificationEvent(GuiEventType.GENERATE_ALL_ELEMENTS, event.getElementType(),
                    modelElement.getName()), mainWindowController.getStatusText()));
        }
        updateViewModel(event, viewModelElement, modelElement);
    }

    /**
     * Handles the model event for the repository view.
     *
     * @param eventType
     * @param viewModelElement
     */
    private void updateRepos(ModelEventType eventType, ViewModelElement viewModelElement) {
        switch (eventType) {
            case ELEMENT_PARSED:
            case ELEMENT_CREATED:
                repoViewModel.addElement(viewModelElement);
                break;
            case ELEMENT_DELETED:
                repoViewModel.removeElement(viewModelElement);
                break;
        }
    }

    /**
     * Handles the model event for the file tree view.
     *
     * @param event
     * @param viewModelElement
     */
    private void updateFileTreeView(ModelEvent event, ViewModelElement viewModelElement) {
        switch (event.getEventType()) {
            case ELEMENT_PARSED:
            case ELEMENT_CREATED:
                mainWindowController.getFileTreeView().addViewModelElement(viewModelElement);
                break;
            case ELEMENT_DELETED:
                mainWindowController.getFileTreeView().removeViewModelElement(viewModelElement);
                break;
            case ELEMENT_ATTRIBUTE_CHANGED:
                if(event.getChangedAttribute().equals("relativeDirectory") ||
                        event.getChangedAttribute().equals("name") ||
                        event.getChangedAttribute().equals("masterPlan")) {
                    mainWindowController.getFileTreeView().removeViewModelElement(viewModelElement);
                    mainWindowController.getFileTreeView().addViewModelElement(viewModelElement);
                }
                break;
            case ELEMENT_FOLDER_DELETED:
                mainWindowController.getFileTreeView().removeFolder(event.getChangedAttribute());
                break;
        }
    }

    /**
     * Handles the model event for the the view model elements.
     *
     * @param event
     * @param viewModelElement
     * @param planElement
     */
    private void updateViewModel(ModelEvent event, ViewModelElement viewModelElement, PlanElement planElement) {
        switch (event.getEventType()) {
            case ELEMENT_DELETED:
            case ELEMENT_REMOVED:
                viewModelManager.removeElement(event.getParentId(), viewModelElement, event.getRelatedObjects());

                //When Behaviour is deleted, regenerate the BehaviourCreator.
                if(viewModelElement instanceof BehaviourViewModel && event.getEventType().equals(ModelEventType.ELEMENT_DELETED)) {
                    mainWindowController.waitOnProgressLabel(() -> generateCode(new GuiModificationEvent(GuiEventType.GENERATE_ALL_ELEMENTS, event.getElementType(),
                            event.getElement().getName()), mainWindowController.getStatusText()));
                }
                break;
            case ELEMENT_ATTRIBUTE_CHANGED:
                viewModelManager.changeElementAttribute(viewModelElement, event.getChangedAttribute(), event.getNewValue(), event.getOldValue());
                if(event.getChangedAttribute().equals("name")) {
                    viewModelElement.setName(planElement.getName());
                    updateFileTreeView(event, viewModelElement);
                }
                if(event.getChangedAttribute().equals("masterPlan")) {
                    updateFileTreeView(event, viewModelElement);
                }
                break;
            case ELEMENT_PARSED:
            case ELEMENT_CREATED:
            case ELEMENT_ADDED:
                viewModelManager.addElement(event);
                break;
            case ELEMENT_CONNECTED:
                viewModelManager.connectElement(event);
                break;
            case ELEMENT_DISCONNECTED:
                viewModelManager.disconnectElement(event);
                break;
            case ELEMENT_CHANGED_POSITION:
                viewModelManager.changePosition((PlanElementViewModel) viewModelElement, event);
                break;
            default:
                System.out.println("Controller.updateViewModel(): Event type " + event.getEventType() + " is not handled.");
                break;
        }

        if (event.getUiElement() != null && event.getUiElement().getBendPoints().size() != 0) {
            TransitionViewModel transition = (TransitionViewModel) viewModelElement;
            transition.getBendpoints().clear();
            for (BendPoint bendPoint : event.getUiElement().getBendPoints()) {
                BendPointViewModel bendPointViewModel = (BendPointViewModel) viewModelManager.getViewModelElement(bendPoint);
                transition.addBendpoint(bendPointViewModel);
            }
            ModelEvent modelEvent = new ModelEvent(ModelEventType.ELEMENT_CREATED, planElement, Types.BENDPOINT);
            updateViewModel(modelEvent, viewModelElement, planElement);
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
                usageViewModelElements.add(viewModelManager.getViewModelElement(planElement));
            }
        }
        return usageViewModelElements;
    }

    /**
     * Called by the configuration manager, if the active configuration has changed.
     */
    public void handleConfigurationChanged() {
        // save everything not saved
        EditorTabPane editorTabPane = mainWindowController.getEditorTabPane();
        for (Tab tab : editorTabPane.getTabs()) {
            EventHandler<Event> handler = tab.getOnCloseRequest();
            if (handler != null) {
                handler.handle(null);
            }
        }

        Configuration activeConfiguration = configurationManager.getActiveConfiguration();

        // clear GUI
        editorTabPane.getTabs().clear();
        repoTabPane.clearGuiContent();
        repoViewModel.clear();
        ((FileTreeViewContextMenu) mainWindowController.getFileTreeView().getContextMenu()).showTaskrepositoryItem(true);
        ((FileTreeViewContextMenu) mainWindowController.getFileTreeView().getContextMenu()).showRoleSetItem(true);

        mainWindowController.setUpFileTreeView(activeConfiguration.getPlansPath(), activeConfiguration.getRolesPath(), activeConfiguration.getTasksPath());

        // load model from path
        modelManager.setPlansPath(activeConfiguration.getPlansPath());
        modelManager.setRolesPath(activeConfiguration.getRolesPath());
        modelManager.setTasksPath(activeConfiguration.getTasksPath());

        modelManager.loadModelFromDisk();
        repoViewModel.initGuiContent();
    }

    /**
     * Called by the main window controlled at the end of its initialized method.
     */
    @Override
    public void handleGuiInitialized() {
        mainWindowController.enableMenuBar();
        editorTabPane = mainWindowController.getEditorTabPane();
        Configuration activeConfiguration = configurationManager.getActiveConfiguration();
        if (activeConfiguration != null) {
            mainWindowController.setUpFileTreeView(activeConfiguration.getPlansPath(), activeConfiguration.getRolesPath(), activeConfiguration.getTasksPath());
            new Thread(fileSystemEventHandler).start(); // <- will be stopped by the PlanDesigner.isRunning() flag
            modelManager.loadModelFromDisk();
        }
        repoTabPane = mainWindowController.getRepositoryTabPane();
        repoViewModel.setRepositoryTabPane(repoTabPane);
        repoViewModel.initGuiContent();
        editorTabPane.setGuiModificationHandler(this);
    }

    /**
     * Called by the context menu for creating plans, behaviours etc.
     *
     * @param event
     */
    @Override
    public void handle(GuiModificationEvent event) {
        ModelModificationQuery mmq;
        switch (event.getEventType()) {
            case CREATE_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.CREATE_ELEMENT, event.getAbsoluteDirectory(), event.getElementType(), event.getName());
                mmq.setParentId(event.getParentId());
                mmq.setComment(event.getComment());
                mmq.setRelatedObjects(event.getRelatedObjects());
                break;
            case DELETE_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.DELETE_ELEMENT, event.getAbsoluteDirectory(), event.getElementType(), event.getName());
                mmq.setElementId(event.getElementId());
                mmq.setParentId(event.getParentId());
                mmq.setRelatedObjects(event.getRelatedObjects());
                break;
            case SAVE_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.SAVE_ELEMENT, event.getAbsoluteDirectory(), event.getElementType(), event.getName());
                mmq.setElementId(event.getElementId());
                break;
            case ADD_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.ADD_ELEMENT);
                mmq.setElementId(event.getElementId());
                mmq.setElementType(event.getElementType());
                mmq.setParentId(event.getParentId());
                mmq.setName(event.getName());
                mmq.setComment(event.getComment());
                mmq.setX(event.getX());
                mmq.setY(event.getY());
                mmq.setRelatedObjects(event.getRelatedObjects());
                break;
            case COPY_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.COPY_ELEMENT, event.getAbsoluteDirectory(), event.getElementType(), event.getName());
                mmq.setElementId(event.getElementId());
                mmq.setParentId(event.getParentId());
                break;
            case REMOVE_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.REMOVE_ELEMENT);
                mmq.setElementId(event.getElementId());
                mmq.setElementType(event.getElementType());
                mmq.setParentId(event.getParentId());
                break;
            case REMOVE_ALL_ELEMENTS:
                mmq = new ModelModificationQuery(ModelQueryType.REMOVE_ALL_ELEMENTS);
                mmq.setElementType(event.getElementType());
                mmq.setElementId(event.getElementId());
                mmq.setParentId(event.getParentId());
                break;
            case RELOAD_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.RELOAD_ELEMENT);
                mmq.setElementId(event.getElementId());
                mmq.setElementType(event.getElementType());
                break;
            case CHANGE_ELEMENT:
                mmq = new ModelModificationQuery(ModelQueryType.CHANGE_ELEMENT);
                mmq.setElementType(event.getElementType());
                mmq.setParentId(event.getParentId());
                mmq.setElementId(event.getElementId());
                mmq.setRelatedObjects(event.getRelatedObjects());
                if (event instanceof GuiChangeAttributeEvent) {
                    GuiChangeAttributeEvent guiChangeAttributeEvent = (GuiChangeAttributeEvent) event;
                    mmq.setAttributeType(guiChangeAttributeEvent.getAttributeType());
                    mmq.setAttributeName(guiChangeAttributeEvent.getAttributeName());
                    mmq.setNewValue(guiChangeAttributeEvent.getNewValue());
                    mmq.setOldValue(guiChangeAttributeEvent.getOldValue());
                }
                break;
            case CHANGE_POSITION:
                mmq = new ModelModificationQuery(ModelQueryType.CHANGE_POSITION);
                mmq.setElementType(event.getElementType());
                mmq.setElementId(event.getElementId());
                mmq.setParentId(event.getParentId());
                mmq.setX(event.getX());
                mmq.setY(event.getY());
                mmq.setRelatedObjects(event.getRelatedObjects());
                break;
            case MOVE_FILE:
                mmq = new ModelModificationQuery(ModelQueryType.MOVE_FILE, event.getAbsoluteDirectory(), event.getElementType(), event.getName());
                mmq.setElementId(event.getElementId());
                break;
            case GENERATE_ELEMENT:
            case GENERATE_ALL_ELEMENTS:
                mmq = null;
                break;
            default:
                mmq = null;
        }
        this.modelManager.handleModelModificationQuery(mmq);
    }

    /**
     * Called when something relevant in the filesystem has changed.
     *
     * @param event
     * @param path
     */
    public void handleFileSystemEvent(WatchEvent event, Path path) {
        // A change in a sub-directory also creates an event for the parent-directory. This event must be ignored,
        // because its filename is only the name of the subdirectory
        /*if (!path.toFile().isFile()) {
            return;
        }*/

        WatchEvent.Kind kind = event.kind();
        ModelModificationQuery mmq;
        if (kind.equals((StandardWatchEventKinds.ENTRY_MODIFY))) {
            mmq = new ModelModificationQuery(ModelQueryType.PARSE_ELEMENT, path.toString());
        } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            PlanElement element = modelManager.getPlanElement(path.toString());
            if (element == null) {
                return;
            }
            mmq = new ModelModificationQuery(ModelQueryType.DELETE_ELEMENT, path.toString());
            mmq.setElementId(element.getId());
            mainWindowController.getFileTreeView().updateDirectories(path);
        } else if (kind.equals((StandardWatchEventKinds.ENTRY_CREATE))) {
            if (path.toFile().isDirectory()) {
                mainWindowController.getFileTreeView().updateDirectories(path);
            } else {
                System.out.println("Controller: ENTRY_CREATE filesystem event is ignored!");
            }
            return;
        } else {
            System.err.println("Controller: Unknown filesystem event elementType received that gets ignored!");
            return;
        }
        mainWindowController.getFileTreeView().updateDirectories(path);
        this.modelManager.handleModelModificationQuery(mmq);
    }

    public void handleNoTaskRepositoryNotification() {
        HashMap<String, Double> params = configEventHandler.getPreferredWindowSettings();
        I18NRepo i18NRepo = I18NRepo.getInstance();
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(i18NRepo.getString("label.warn"));
        alert.setHeaderText(i18NRepo.getString("label.error.missing.taskrepository"));
        alert.setContentText(i18NRepo.getString("label.error.missing.taskrepository.choice"));
        alert.setX(params.get("x") + Screen.getPrimary().getVisualBounds().getWidth() / 2 - alert.getDialogPane().getWidth() * 1.5);
        alert.setY(params.get("y") + Screen.getPrimary().getVisualBounds().getHeight() / 2 - alert.getDialogPane().getHeight() * 1.5);

        ButtonType createTaskRepositoryBtn = new ButtonType(i18NRepo.getString("action.create.taskrepository"));
        ButtonType confirmBtn = new ButtonType(i18NRepo.getString("action.openanyway"));
        ButtonType closeBtn = new ButtonType(i18NRepo.getString("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(createTaskRepositoryBtn, confirmBtn, closeBtn);

        alert.showAndWait();
        if (alert.getResult() == confirmBtn) {
            alert.close();
        } else if (alert.getResult() == closeBtn) {
            Platform.exit();
            System.exit(0);
        } else if (alert.getResult() == createTaskRepositoryBtn) {
            CreateNewDialogController createNewDialogController;
            if (configurationManager.getActiveConfiguration() != null) {
                createNewDialogController = ((FileTreeViewContextMenu) MainWindowController.getInstance().getFileTreeView()
                        .getContextMenu()).getNewResourceMenu().createFileDialog(new File(configurationManager.getActiveConfiguration().getTasksPath()), Types.TASKREPOSITORY);
            } else {
                createNewDialogController = ((FileTreeViewContextMenu) MainWindowController.getInstance().getFileTreeView()
                        .getContextMenu()).getNewResourceMenu().createFileDialog(null, Types.TASKREPOSITORY);
            }
            createNewDialogController.getStage().setX(alert.getX() + createNewDialogController.getMainVBox().getPrefWidth() / 2);
            createNewDialogController.getStage().setY(alert.getY());
            createNewDialogController.getStage().setAlwaysOnTop(true);
            createNewDialogController.showAndWait();
        }
    }

    @Override
    public void handleWrongTaskRepositoryNotification(String planName, long taskID) {
        HashMap<String, Double> params = configEventHandler.getPreferredWindowSettings();
        I18NRepo i18NRepo = I18NRepo.getInstance();
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(i18NRepo.getString("label.warn"));
        alert.setHeaderText(i18NRepo.getString("label.error.wrong.taskrepository") + " " + taskID + "  "
                + i18NRepo.getString("label.error.wrong.taskrepository2") + " " + planName+ ".");
        alert.setX(params.get("x") + Screen.getPrimary().getVisualBounds().getWidth() / 2 - alert.getDialogPane().getWidth());
        alert.setY(params.get("y") + Screen.getPrimary().getVisualBounds().getHeight() / 2 - alert.getDialogPane().getHeight());

        ButtonType closeBtn = new ButtonType(i18NRepo.getString("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(closeBtn);

        alert.showAndWait();
        Platform.exit();
        System.exit(0);
    }

    @Override
    public ViewModelElement getViewModelElement(long id) {
        return viewModelManager.getViewModelElement(modelManager.getPlanElement(id));
    }

    @Override
    public void handleCloseTab(long planElementId) {
        for (Tab tab : editorTabPane.getTabs()) {
            if (tab instanceof AbstractPlanTab) {
                AbstractPlanTab abstractPlanTab = (AbstractPlanTab) tab;
                if (abstractPlanTab.getSerializableViewModel().getId() == planElementId) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            editorTabPane.getTabs().remove(tab);
                        }
                    });
                    break;
                }
            }
            if (tab instanceof TaskRepositoryTab) {
                TaskRepositoryTab taskRepositoryTab = (TaskRepositoryTab) tab;
                if (taskRepositoryTab.getSerializableViewModel().getId() == planElementId) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            editorTabPane.getTabs().remove(tab);
                        }
                    });
                    break;
                }
            }
        }
    }

    @Override
    public RepositoryViewModel getRepoViewModel() {
        return repoViewModel;
    }

    @Override
    public long getTaskRepositoryID() {
        return modelManager.getTaskRepository().getId();
    }

    @Override
    public void storeAll() {
        for (Plan plan: modelManager.getPlans()) {
            modelManager.storePlanElement(Types.PLAN, plan, true);
        }
        for (Behaviour behaviour: modelManager.getBehaviours()) {
            modelManager.storePlanElement(Types.BEHAVIOUR, behaviour, true);
        }
        for (PlanType planType: modelManager.getPlanTypes()) {
            modelManager.storePlanElement(Types.PLANTYPE, planType, true);
        }
        modelManager.storePlanElement(Types.TASKREPOSITORY, modelManager.getTaskRepository(), true);
        modelManager.storePlanElement(Types.ROLESET, modelManager.getRoleSet(), true);
    }

    @Override
    public void disableUndo(boolean disable) {
        mainWindowController.disableUndo(disable);
    }

    @Override
    public void disableRedo(boolean disable) {
        mainWindowController.disableRedo(disable);
    }

    @Override
    public void handleUndo() {
        modelManager.undo();
    }

    @Override
    public void handleRedo() {
        modelManager.redo();
    }

    @Override
    public List<File> getGeneratedFilesForAbstractPlan(AbstractPlan abstractPlan) {
        if(abstractPlan instanceof Behaviour) {
            List<File> fileList = generatedSourcesManager.getGeneratedFilesForBehaviour((Behaviour) abstractPlan);
            fileList.addAll(generatedSourcesManager.getGeneratedConstraintFilesForBehaviour((Behaviour) abstractPlan));
            return fileList;
        } else if (abstractPlan instanceof Plan) {
            List<File> fileList = generatedSourcesManager.getGeneratedConditionFilesForPlan(abstractPlan);
            fileList.addAll(generatedSourcesManager.getGeneratedConstraintFilesForPlan(abstractPlan));
            return fileList;
        } else {
            return new ArrayList<>();
        }
    }
    @Override
    public void generateAutoGeneratedFilesForAbstractPlan(AbstractPlan abstractPlan){
        mainWindowController.waitOnProgressLabel(() -> generateCode(new GuiModificationEvent(GuiEventType.GENERATE_ALL_ELEMENTS, "behaviour",
                abstractPlan.getName()), mainWindowController.getStatusText()));
    }

    @Override
    public File showGeneratedSourceHandler(long modelElementId) {
        AbstractPlan abstractPlan = (AbstractPlan) this.modelManager.getPlanElement(modelElementId);
        List<File> generatedFilesList = getGeneratedFilesForAbstractPlan(abstractPlan);
        return generatedFilesList.get(1);
    }

    // ALICA Engine and DebugView stuff

    private Map<Long, String> currentAgents;
    private Map<Long, Long> agentsTabs;

    private Process roscoreProcess;
    private List<Process> pdAlicaRunnerProcesses; // needs more than one process

    private String lastAEIMessage;
    private Thread messageReceiver;
    private boolean isDebugRunning = false;

    @Override
    public void handleAlicaMessageReceived(long senderId, String masterPlan, String currentPlan, String currentState, String currentRole, String currentTask, long[] agentsWithMe) {
        if (currentAgents == null) currentAgents = new HashMap<>();
        String oldPlan = currentAgents.put(senderId, currentPlan);

        Platform.runLater(() -> {
            System.out.println("Loading Plan " + currentPlan + " and highlighting state " + currentState + "...");

            // Just open a new tab when we either have a new agent (oldPlan == null) or when the the agent has changed
            // the plan
            if (oldPlan == null || !oldPlan.equals(currentPlan)) {

                if (agentsTabs == null) agentsTabs = new HashMap<>();

                if (oldPlan != null) {
                    // close the old tab for the agent
                    handleCloseTab(agentsTabs.get(senderId));
                }

                // Find the plan from the repository that matches the name of the given currentPlan
                ViewModelElement currentPlanViewModelElement = getRepoViewModel().getPlans().stream()
                        .filter(v -> v.getName().equals(currentPlan))
                        .findFirst()
                        .orElse(null);

                if (currentPlanViewModelElement != null) {
                    // Open the plan if it was found
                    SerializableViewModel svm = (SerializableViewModel) currentPlanViewModelElement;
                    svm.setDebugSenderId("[" + senderId + "] ");
                    agentsTabs.put(senderId, svm.getId());
                    mainWindowController.openFile(svm);

                }
            }

            for (Tab t : mainWindowController.getEditorTabPane().getTabs()) {
                if (t instanceof PlanTab) {
                    PlanTab tab = (PlanTab) t;

                    // Find the StateContainer that represents the given currentState within the opened Tab
                    StateContainer currentlyActiveStateContainer = tab.getPlanEditorGroup().getStateContainers().values().stream()
                            .filter(sc -> sc.getState().getName().equals(currentState))
                            .findFirst()
                            .orElse(null);

                    // and highlight it
                    if (currentlyActiveStateContainer != null) {
                        tab.setCurrentDebugContainer(currentlyActiveStateContainer, senderId + "");
                        currentlyActiveStateContainer.getPlanEditorGroup().relocate(currentlyActiveStateContainer.getLayoutX(), currentlyActiveStateContainer.getLayoutY());
                    } else {
                        System.err.println("Could not highlight state: " + currentState);
                    }

                }
            }


        });
    }

    @Override
    public boolean runAlica(String name, String masterPlan, String roleset) {
        String alicaEnginePath = configurationManager.getAlicaEnginePath();
        String rolesPath = configurationManager.getActiveConfiguration().getRolesPath();
        try {
            // first determine if roscore is already runnig
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", "pgrep -f roscore");
            Process rcRunning = pb.start();
            String rcRunningOutput = "";
            final BufferedReader reader = new BufferedReader(new InputStreamReader(rcRunning.getInputStream()));
            boolean isRCRunning = reader.lines().count() > 0;
            rcRunning.waitFor();
            rcRunning.destroy();
            reader.close();

            if (!isRCRunning) {
                // start roscore, because we still need it
                pb = new ProcessBuilder("bash", "-c", "echo 'Sourcing setup.bash'; source /opt/ros/melodic/setup.bash; echo 'Starting roscore...'; roscore");
                pb.environment().put("PATH", pb.environment().get("PATH") + ":/opt/ros/melodic/bin");
                //pb.inheritIO();
                roscoreProcess = pb.start();
            }

            if (pdAlicaRunnerProcesses == null) pdAlicaRunnerProcesses = new ArrayList<>();
            // Load pd_alica_runner
            pb = new ProcessBuilder("bash", "-c", "echo Starting AlicaEngine runner...; " +
                    alicaEnginePath +
                    " -m " + masterPlan +
                    " -rd " + rolesPath +
                    " -r " + roleset +
                    " -sim");
            pb.directory(Paths.get(rolesPath).getParent().getParent().toFile());
            pb.environment().put("PATH", pb.environment().getOrDefault("PATH", "") + ":/opt/ros/melodic/bin");
            pb.environment().put("ROBOT", name);
            pb.environment().put("LD_LIBRARY_PATH", "/opt/ros/melodic/lib" + pb.environment().getOrDefault("LD_LIBRARY_PATH", ""));
            pb.environment().put("ROS_MASTER_URI", "http://localhost:11311");
            //pb.inheritIO();
            System.out.println("Starting Agent with name = " + name);
            pdAlicaRunnerProcesses.add(pb.start());

            // get settings for Subscriber from AlicaCapnzProxy.conf
            List<String> alicaCapnzeroProxy = Files.readAllLines(Paths.get(Paths.get(rolesPath).getParent().toString(), "AlicaCapnzProxy.conf"));
            alicaCapnzeroProxy.replaceAll(String::strip);
            alicaCapnzeroProxy.removeIf(String::isEmpty);

            List<String> topics = alicaCapnzeroProxy.subList(
                    alicaCapnzeroProxy.indexOf("[Topics]") + 1,
                    alicaCapnzeroProxy.indexOf("[!Topics]"));

            String alicaEngineInfoTopic = topics.stream()
                    .filter(s -> s.startsWith("alicaEngineInfoTopic"))
                    .findFirst()
                    .orElse("alicaEngineInfoTopic=\"aeinfo\"")
                    .split("=")[1]
                    .replaceAll(" ", "")
                    .replaceAll("\"", "");

            List<String> comm = alicaCapnzeroProxy.subList(
                    alicaCapnzeroProxy.indexOf("[Communication]") + 1,
                    alicaCapnzeroProxy.indexOf("[!Communication]"));
            String url = comm.stream()
                    .filter(s -> s.startsWith("URL"))
                    .findFirst()
                    .orElse("URL=224.0.0.2:5555")
                    .split("=")[1].strip().replaceAll("\"", "");;
            int commType = Integer.parseInt(comm.stream()
                    .filter(s -> s.startsWith("transport"))
                    .findFirst()
                    .orElse("transport=0")
                    .split("=")[1].strip());

            Protocol protocol = Protocol.values()[commType];

            // start listening
            isDebugRunning = true;
            messageReceiver = new Thread(() -> {
                Subscriber sub = new Subscriber();
                sub.setGroupName(alicaEngineInfoTopic);
                sub.subscribe(protocol, url);

                while (true) {
                    if (isDebugRunning) {
                        try {
                            Thread.sleep(100);
                            String message = sub.getSerializedMessage();
                            if (!message.isEmpty()) {
                                onAlicaEngineInfoReceived(message);
                            }
                        } catch (InterruptedException e) {
                            // The sleep was probably interrupted by the stopAlica() method in order to stop the loop.
                            // No need to do anything.
                        }
                    } else {
                        // Stop the Thread when it is not supposed to run (anymore)
                        return;
                    }
                }
            });
            messageReceiver.start();

            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void stopAlica() throws InterruptedException {
        if (roscoreProcess == null && pdAlicaRunnerProcesses == null) {
            return;
        } else {
            if (pdAlicaRunnerProcesses != null) {

                // stop the message receiver thread
                isDebugRunning = false;
                messageReceiver.interrupt();

                // kill all pd_alica_runners
                for (Process runner : pdAlicaRunnerProcesses) {
                    runner.destroyForcibly();
                    runner.waitFor();
                }

                pdAlicaRunnerProcesses.clear();
            }
            if (roscoreProcess != null) {
                roscoreProcess.waitFor();
                roscoreProcess.destroyForcibly();
                roscoreProcess = null;
            }
        }
    }

    /**
     * Checks the received message and if it changed from previous call, shows the
     * appropriate plan.
     *
     * @param msg The message received from the ALICA engine, topic AlicaEngineInfo.
     */
    private void onAlicaEngineInfoReceived(String msg) {
        if (msg.length() == 0) return;
        if (!msg.equals(lastAEIMessage)) {
            System.out.println(msg);
            lastAEIMessage = msg;
            /*
            msg =
            (senderId = (value = "\x01\x00\x00\x00", type = 1),
            masterPlan = "ServeMaster",
            currentPlan = "ServeMaster",
            currentState = "Stop",
            currentRole = "Assistant",
            currentTask = "DefaultTask",
            agentIdsWithMe = [(value = "\x01\x00\x00\x00", type = 1)])
             */

            long senderId = 0;
            String masterPlan = "", currentPlan = "", currentState = "", currentRole = "", currentTask = "";
            long[] agentsWithMe = {0};
            msg = msg.replaceAll(" ", "");

            int endSenderId = msg.indexOf(")");

            String senderKeyValue = msg.substring(1, endSenderId + 1);
            String senderIdString = senderKeyValue.split("senderId=")[1].split(",")[0].split("=")[1].replaceAll("\"", "");
            senderIdString = StringEscapeUtils.unescapeJava(senderIdString.replace("\\x", "#"));
            StringBuilder str = new StringBuilder();
            List<String> hexes = Arrays.asList(senderIdString.split("#"));
            Collections.reverse(hexes);
            for (String hex : hexes) {
                str.append(hex);
            }
            senderId = Long.parseLong(str.toString(), 16);

//            System.out.println(senderId);

            String keyvalues = msg.substring(endSenderId + 2, msg.lastIndexOf("[") - ",agentIdsWithMe=".length());

            for (String keyValue : keyvalues.split(",")) {
                String[] split = keyValue.split("=");
                switch (split[0]) {
                    case "masterPlan":
                        masterPlan = split[1].replaceAll("\"", "");
                        break;
                    case "currentPlan":
                        currentPlan = split[1].replaceAll("\"", "");
                        break;
                    case "currentState":
                        currentState = split[1].replaceAll("\"", "");
                        break;
                    case "currentRole":
                        currentRole = split[1].replaceAll("\"", "");
                        break;
                    case "currentTask":
                        currentTask = split[1].replaceAll("\"", "");
                        break;
                }
            }

            handleAlicaMessageReceived(senderId, masterPlan, currentPlan, currentState, currentRole, currentTask, agentsWithMe);

        } else {
            // Message has not changed, do nothing
        }
    }
}
