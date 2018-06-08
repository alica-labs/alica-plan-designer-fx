package de.uni_kassel.vs.cn.planDesigner.view.filebrowser;

import de.uni_kassel.vs.cn.planDesigner.common.FileWrapper;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FileTreeItem extends TreeItem<FileWrapper> {

    public FileTreeItem(FileWrapper value, Node graphic) {
        super(value, graphic);
        if (value.unwrap().isDirectory()) {
            for (File content : value.unwrap().listFiles()) {
                Image listItemImage = getImageForFileType(content);
                if (listItemImage == null) {
                    continue;
                }
                getChildren().add(new FileTreeItem(new FileWrapper(content), new ImageView(listItemImage)));
            }
            getChildren().sort(Comparator.comparing(o -> o.getValue().unwrap().toURI().toString()));
        }
    }

    public void updateDirectory(WatchEvent.Kind kind, Path child) {
        FileWrapper value = getValue();
        File newFile = child.toFile();
        List<TreeItem<FileWrapper>> collect = getChildren()
                .stream()
                .filter(e -> newFile.getAbsolutePath().contains(e.getValue().unwrap().getAbsolutePath()))
                .collect(Collectors.toList());
        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE) && newFile.isDirectory() && collect.size() == 1) {
            ((FileTreeItem)collect.get(0)).updateDirectory(kind, child);
            return;
        }

        if (collect.size() == 1 && collect.get(0).getChildren().stream().noneMatch(e -> newFile.getAbsolutePath().contains(e.getValue().unwrap().getAbsolutePath()))) {
            if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY) || kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                    if (collect.get(0).getValue().unwrap().equals(newFile)) {
                        getChildren().remove(collect.get(0));
                    } else {
                        ((FileTreeItem)collect.get(0)).updateDirectory(kind, child);
                    }
                }
                if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                    for (File content : value.unwrap().listFiles()) {

                        boolean isAlreadyKnownToTreeItem = getChildren().stream().anyMatch(e -> e.getValue().unwrap().equals(content));
                        if (isAlreadyKnownToTreeItem == false) {
                            Image listItemImage = getImageForFileType(content);
                            if (listItemImage == null) {
                                continue;
                            }
                            getChildren().add(new FileTreeItem(new FileWrapper(content), new ImageView(listItemImage)));
                            getChildren().sort(Comparator.comparing(o -> o.getValue().unwrap().toURI().toString()));
                        }
                    }
                }
            } else if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                boolean isAlreadyKnownToTreeItem = getChildren().stream().anyMatch(e -> e.getValue().unwrap().equals(newFile));
                if (isAlreadyKnownToTreeItem == false && newFile.getParentFile().equals(value.unwrap())) {
                    Image listItemImage = getImageForFileType(newFile);
                    if (listItemImage != null) {
                        getChildren().add(new FileTreeItem(new FileWrapper(newFile), new ImageView(listItemImage)));
                        getChildren().sort(Comparator.comparing(o -> o.getValue().unwrap().toURI().toString()));
                    }
                } else {
                    ((FileTreeItem)collect.get(0)).updateDirectory(kind, child);
                }
            }
        } else if (newFile.isDirectory() && newFile.getParentFile().equals(value.unwrap())){
            getChildren().add(new FileTreeItem(new FileWrapper(newFile), new ImageView(getImageForFileType(newFile))));
            getChildren().sort(Comparator.comparing(o -> o.getValue().unwrap().toURI().toString()));
        } else if(newFile.getParentFile().equals(value.unwrap()) && newFile.toString().endsWith("pmlex") == false
                && getChildren().stream().noneMatch(e -> e.getValue().unwrap().equals(newFile))) {
            getChildren().add(new FileTreeItem(new FileWrapper(newFile), new ImageView(getImageForFileType(newFile))));
            getChildren().sort(Comparator.comparing(o -> o.getValue().unwrap().toURI().toString()));
        } else {
            collect.forEach(e -> ((FileTreeItem)e).updateDirectory(kind, child));
        }
        setExpanded(true);
    }

    private Image getImageForFileType(File content) {
        Image listItemImage;
        if (content.getName().endsWith(".beh")) {
            listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/behaviour24x24.png")));
        } else if (content.getName().endsWith(".pml")) {
//            try {
//                Plan plan = (Plan)EMFModelUtils.loadAlicaFileFromDisk(content);
//                if (plan.isMasterPlan()) {
//                    listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/masterplan24x24.png")));
//                } else {
                    listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/plan24x24.png")));
//                }
//            } catch (IOException e1) {
//                e1.printStackTrace();
//                return null;
//            }
        } else if (content.getName().endsWith(".pty")) {
            listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/plantype24x24.png")));
        } else if (content.getName().endsWith(".tsk")) {
            listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/tasks24x24.png")));
        } else if (content.isDirectory()) {
            listItemImage = new Image((getClass().getClassLoader().getResourceAsStream("images/folder24x24.png")));
        } else  {
            return null;
        }
        return listItemImage;
    }
}
