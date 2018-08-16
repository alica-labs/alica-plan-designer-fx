package de.uni_kassel.vs.cn.planDesigner.view.model;

import de.uni_kassel.vs.cn.planDesigner.view.editor.tab.taskRepoTab.TaskRepositoryTab;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;

public class TaskRepositoryViewModel {
    private ObservableList<ViewModelElement> tasks;
    private TaskRepositoryTab taskRepositoryTab;
    private boolean isDirty;

    public TaskRepositoryViewModel() {
        tasks = FXCollections.observableArrayList(new ArrayList<>());
        tasks.addListener(new ListChangeListener<ViewModelElement>() {
            /**
             * Refills the GUI and sets the dirty flag, accordingly.
             * @param c
             */
            @Override
            public void onChanged(Change<? extends ViewModelElement> c) {
                if (taskRepositoryTab == null) {
                    return;
                }
                taskRepositoryTab.clearGuiContent();
                taskRepositoryTab.addElements(tasks);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        setDirty(true);
                    }
                });

            }
        });
        isDirty = false;
    }

    public void setTaskRepositoryTab(TaskRepositoryTab taskRepositoryTab) {
        this.taskRepositoryTab = taskRepositoryTab;
        if (taskRepositoryTab == null) {
            return;
        }

        taskRepositoryTab.clearGuiContent();
        taskRepositoryTab.addElements(tasks);
    }

    /**
     * Should only be called with "false", when the task repo was
     * serialized.
     * @param dirty
     */
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
        if (this.taskRepositoryTab != null) {
            this.taskRepositoryTab.setDirty(isDirty);
        }
    }

    public void addTask(ViewModelElement task) {
        this.tasks.add(task);
    }

    public void removeTask(long id) {
        for(ViewModelElement task : tasks) {
            if(task.getId() == id) {
                this.tasks.remove(task);
                break;
            }
        }
    }

    public void clearTasks() {
        tasks.clear();
    }
}
