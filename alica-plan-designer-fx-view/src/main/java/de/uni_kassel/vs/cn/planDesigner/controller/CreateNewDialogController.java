package de.uni_kassel.vs.cn.planDesigner.controller;

import de.uni_kassel.vs.cn.planDesigner.PlanDesignerApplication;
import de.uni_kassel.vs.cn.planDesigner.events.ResourceCreationEvent;
import de.uni_kassel.vs.cn.planDesigner.handlerinterfaces.IResourceCreationHandler;
import de.uni_kassel.vs.cn.planDesigner.view.I18NRepo;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class CreateNewDialogController implements Initializable {

    private static final Logger LOG = LogManager.getLogger(CreateNewDialogController.class);

    @FXML
    private Label pathLabel;

    @FXML
    private TextField pathTextField;

    @FXML
    private Button openFileChooserButton;

    @FXML
    private Label nameLabel;

    @FXML
    private TextField nameTextField;

    @FXML
    private Button createButton;

    private File initialDirectoryHint;
    private String type;
    private I18NRepo i18NRepo;
    private IResourceCreationHandler resourceCreationHandler;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        i18NRepo = I18NRepo.getInstance();
        pathLabel.setText(i18NRepo.getString("label.choose.parentDirectory"));
        openFileChooserButton.setOnAction(e -> {
            Node source = (Node) e.getSource();
            openFileChooser((Stage) source.getScene().getWindow());
        });
        openFileChooserButton.setText(i18NRepo.getString("action.choose"));
        createButton.setText(i18NRepo.getString("action.create"));
        createButton.setOnAction(e -> createResource());
    }

    public void setInitialDirectoryHint(File initialDirectoryHint) {
        if (initialDirectoryHint == null) {
            return;
        }

        this.initialDirectoryHint = initialDirectoryHint;
        if (!initialDirectoryHint.isDirectory()) {
            this.initialDirectoryHint = initialDirectoryHint.getParentFile();
        }
        pathTextField.setText(this.initialDirectoryHint.getAbsolutePath());
    }

    public void setResourceCreationHandler (IResourceCreationHandler resourceCreationHandler) {
        this.resourceCreationHandler = resourceCreationHandler;
    }

    private void openFileChooser(Stage stage) {
        DirectoryChooser fileChooser = new DirectoryChooser();

        if (initialDirectoryHint != null) {
            if (initialDirectoryHint.isDirectory()) {
                fileChooser.setInitialDirectory(initialDirectoryHint);
            } else {
                fileChooser.setInitialDirectory(new File(initialDirectoryHint.getParent()));
            }
        }

        File chosenFile = fileChooser.showDialog(stage);
        String parentPath = chosenFile.getAbsolutePath();

        pathTextField.setText(parentPath);

    }

    private void createResource() {
        String name = trimEnding(nameTextField.getText());
        LOG.info("Corrected name is " + name);

        if (name == null || name.isEmpty() || name.matches(PlanDesignerApplication.forbiddenCharacters)) {
            ErrorWindowController.createErrorWindow(i18NRepo.getString("label.error.save.name") + "'" + name + "'", null);
            return;
        }

        if (type == null) {
            ErrorWindowController.createErrorWindow(i18NRepo.getString("label.error.create.type"), null);
            return;
        }

        // Special handling for creating folders
        if (type.equals(i18NRepo.getString("alicatype.folder"))) {
            try {
                Files.createDirectory(new File(Paths.get(pathTextField.getText(), name).toString()).toPath());
            } catch (IOException e) {
                ErrorWindowController.createErrorWindow(i18NRepo.getString("label.error.create.folder"), e);
                return;
            }
            ((Stage) pathTextField.getScene().getWindow()).close();
            return;
        }

        // Notification of resourceCreationHandler for plans, plantypes, etc...
        resourceCreationHandler.handleResourceCreationEvent(new ResourceCreationEvent(pathTextField.getText(), type, name));
        ((Stage) pathTextField.getScene().getWindow()).close();
    }

    /**
     * Cuts the end starting at the last occurrence of '.'
     * @param filename
     * @return filename without ending
     */
    protected String trimEnding(String filename) {
        if (filename != null && !filename.isEmpty() && filename.contains(".")){
            filename = filename.substring(0,filename.length()-filename.lastIndexOf('.')+1);
        }
        return filename;
    }

    public void setType(String type) {
        if (type == null) {
            return;
        }
        this.type = type;
        nameLabel.setText(i18NRepo.getString("label.menu.new." + type) + " " + i18NRepo.getString("label.name") + ":");
    }
}
