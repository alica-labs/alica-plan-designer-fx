package de.uni_kassel.vs.cn.planDesigner.ui.menu;

import de.uni_kassel.vs.cn.planDesigner.aggregatedModel.GeneratedSourcesManager;
import de.uni_kassel.vs.cn.planDesigner.alica.*;
import de.uni_kassel.vs.cn.planDesigner.alica.configuration.WorkspaceManager;
import de.uni_kassel.vs.cn.planDesigner.common.I18NRepo;
import javafx.scene.control.MenuItem;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by marci on 14.06.17.
 */
public class ShowGeneratedSourcesMenuItem<T extends PlanElement> extends MenuItem {
    private String eclipsePath;
    private T planElement;

    public ShowGeneratedSourcesMenuItem(T planElement) {
        super(I18NRepo.getString("label.menu.sources"));
        this.planElement = planElement;
        setOnAction(e -> showSources());
        eclipsePath = new WorkspaceManager().getEclipsePath();
        if (eclipsePath == null  || eclipsePath.length() == 0 || planElement instanceof EntryPoint) {
            setDisable(true);
        }
    }

    private void showSources() {
        if (planElement instanceof AbstractPlan) {
            List<File> allGeneratedFilesForAbstractPlan = GeneratedSourcesManager.get().getAllGeneratedFilesForAbstractPlan((AbstractPlan) planElement);
            for (File generatedSource : allGeneratedFilesForAbstractPlan) {
                try {
                    Runtime.getRuntime().exec(eclipsePath + " " + generatedSource.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (planElement instanceof State) {
                File fileForState = GeneratedSourcesManager.get().getFileForState((State) planElement);
                int lineNumberForState = GeneratedSourcesManager.get().getLineNumberForState((State) planElement);
                try {
                    Runtime.getRuntime()
                            .exec(eclipsePath + " " + fileForState.getAbsolutePath() + "+" + lineNumberForState);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (planElement instanceof Transition) {
                File fileForTransition = GeneratedSourcesManager.get().getFileForTransition((Transition) planElement);
                int lineNumberForTransition = GeneratedSourcesManager.get().getLineNumberForTransition((Transition) planElement);
                try {
                    Runtime.getRuntime()
                            .exec(eclipsePath + " " + fileForTransition.getAbsolutePath() + "+" + lineNumberForTransition);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (planElement instanceof Condition) {
                Condition planElement = (Condition) this.planElement;
                File fileForCondition = GeneratedSourcesManager.get()
                        .getFileForTransition(((Plan)planElement.eResource().getContents().get(0))
                                .getTransitions().get(0));
                int lineNumberForCondition = GeneratedSourcesManager.get().getLineNumberForCondition(planElement);
                try {
                    Runtime.getRuntime()
                            .exec(eclipsePath + " " + fileForCondition.getAbsolutePath() + "+" + lineNumberForCondition);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }


}