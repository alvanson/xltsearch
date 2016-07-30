/* Copyright 2016 Evan A. Thompson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.alvanson.xltsearch;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

class Configurator {
    private static final int SCENE_WIDTH = 640;
    private static final int SCENE_HEIGHT = 480;
    private static final int BUTTON_WIDTH = 120;

    private Config config;
    private boolean dirty;

    private final Stage stage = new Stage();
    private final ListView<String> listView = new ListView<>();
    private final Map<String,ComboBox<String>> optionsMap = new HashMap<>();

    private final ObjectProperty<Catalog> catalog = new SimpleObjectProperty<>();

    Configurator() {
        stage.setTitle("Configuration");
        catalog.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                // draw UI for new catalog
                dirty = false;
                initUI();
            } else {
                // clear UI
                stage.setScene(new Scene(new BorderPane()));
            }
        });
    }

    private void initUI() {
        final BorderPane border = new BorderPane();

        // LEFT
        final VBox leftVBox = new VBox(10);
        leftVBox.setPadding(new Insets(10));

        final HBox newHBox = new HBox(10);
        final TextField newField = new TextField();
        final Button newButton = new Button("New");

        newHBox.getChildren().addAll(newField, newButton);
        newHBox.setHgrow(newField, Priority.ALWAYS);

        List<String> configs = catalog.get().getConfigs();
        Collections.sort(configs);
        listView.setItems(FXCollections.observableList(configs));

        leftVBox.getChildren().addAll(newHBox, listView);
        leftVBox.setVgrow(listView, Priority.ALWAYS);

        border.setLeft(leftVBox);

        // BOTTOM
        final HBox bottomHBox = new HBox(10);
        bottomHBox.setPadding(new Insets(10));

        final Button aboutButton = new Button("About");
        aboutButton.setPrefWidth(BUTTON_WIDTH);
        final Label spacer = new Label();   // ugly hack
        spacer.setMaxWidth(Double.MAX_VALUE);
        final Button loadButton = new Button("Load");
        loadButton.setPrefWidth(BUTTON_WIDTH);
        final Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(BUTTON_WIDTH);

        bottomHBox.getChildren().addAll(aboutButton, spacer, loadButton, cancelButton);
        bottomHBox.setHgrow(spacer, Priority.ALWAYS);
        bottomHBox.setMinHeight(HBox.USE_PREF_SIZE);

        border.setBottom(bottomHBox);

        // CENTER
        drawCenterPane(border);

        // UI BINDINGS
        listView.getSelectionModel().selectedItemProperty().addListener(
                (o, oldValue, newValue) -> {
            dirty = false;
            config = catalog.get().getConfig(newValue);
            drawCenterPane(border);
        });

        // CALLBACKS
        newButton.setOnAction((event) -> {
            // validate name (only allow word characters)
            String name = newField.getText();
            name = name.replaceAll("[^\\w]","");
            newField.clear();
            newField.setPromptText("");
            if (!name.equals("")) {
                int index = Collections.binarySearch(listView.getItems(), name);
                if (index < 0) {    // not found in list (good)
                    // add in sorted order and select
                    listView.getItems().add(-index-1, name);
                    listView.getSelectionModel().select(-index-1);
                } else {
                    listView.getSelectionModel().select(index);
                }
            } else {
                newField.setPromptText("Invalid name");
            }
        });

        aboutButton.setOnAction((event) -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About");
            alert.setHeaderText("XLTSearch " + Config.XLT_VERSION);
            alert.setContentText("Copyright 2016 Evan A. Thompson\n" +
                                 "https://github.com/alvanson/xltsearch");
            alert.showAndWait();
        });

        loadButton.setOnAction((event) -> {
            if (config == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("No configuration selected");
                alert.setContentText("");
                alert.showAndWait();
                return;
            } else if (dirty) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation");
                alert.setHeaderText("Configuration not saved");
                alert.setContentText("Do you want to save?");
                alert.getDialogPane().getButtonTypes().clear();
                alert.getDialogPane().getButtonTypes().addAll(
                    ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                Optional<ButtonType> result = alert.showAndWait();
                if (!result.isPresent() || result.get() == ButtonType.CANCEL) {
                    return;
                } else if (result.get() == ButtonType.YES) {
                    saveConfig(border);
                }
            }
            catalog.get().loadConfig(config.getName());
            hide();
        });

        cancelButton.setOnAction((event) -> hide());

        Scene scene = new Scene(border, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add("stylesheet.css");
        stage.setScene(scene);
    }

    private void drawCenterPane(BorderPane border) {
        final GridPane grid = new GridPane();
        grid.setAlignment(Pos.BASELINE_LEFT);
        grid.setPadding(new Insets(10, 10, 10, 0));

        final ColumnConstraints cc = new ColumnConstraints();
        final ColumnConstraints ccGrow = new ColumnConstraints();
        ccGrow.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc, ccGrow, cc);

        grid.setHgap(10);
        grid.setVgap(10);

        if (config != null) {
            // add each property, one per row
            int row = 0;
            optionsMap.clear();
            for (String property : config.getPropertyNames()) {
                // format property label
                StringBuilder sb = new StringBuilder();
                String[] words = property.split("\\.");
                for (String word : words) {
                    sb.append(word.substring(0, 1).toUpperCase());  // capitalize first letter
                    sb.append(word.substring(1));   // add remaining letters
                    sb.append(" ");
                }
                sb.setCharAt(sb.length()-1, ':');   // replace trailing space with ':'
                final Label label = new Label(sb.toString());
                label.getStyleClass().add("caption");
                label.setMinWidth(Label.USE_PREF_SIZE);

                grid.add(label, 0, row);

                // populate property options
                final ComboBox comboBox = new ComboBox();
                comboBox.setItems(FXCollections.observableArrayList(
                    config.getOptions(property)));
                comboBox.setValue(config.getValue(property));
                comboBox.setMaxWidth(Double.MAX_VALUE);
                comboBox.valueProperty().addListener((o, ov, nv) -> {
                    dirty = true;
                });

                grid.add(comboBox, 1, row, 2, 1);
                // keep references to property comboBoxes
                optionsMap.put(property, comboBox);
                row++;
            }

            grid.add(new Label(" "), 0, row, 3, 1);  // row spacer

            // index status
            final Label statusMessage = new Label(config.getIndexMessage());
            statusMessage.setAlignment(Pos.BASELINE_RIGHT);
            statusMessage.setMaxWidth(Double.MAX_VALUE);

            grid.add(statusMessage, 0, row+1, 3, 1);

            final Button updateIndexButton = new Button("Update Index");
            updateIndexButton.setPrefWidth(BUTTON_WIDTH);
            final Button clearIndexButton = new Button("Clear Index");
            clearIndexButton.setPrefWidth(BUTTON_WIDTH);

            long lastUpdated = config.getLastUpdated();
            if (lastUpdated == Config.INDEX_INVALIDATED) {
                grid.add(clearIndexButton, 2, row+2);
            } else {
                if (lastUpdated < 0) {
                    updateIndexButton.setText("Build Index");
                }
                grid.add(updateIndexButton, 2, row+2);
            }

            grid.add(new Label(" "), 0, row+3, 3, 1);  // row spacer

            final Button saveButton = new Button("Save");
            saveButton.setPrefWidth(BUTTON_WIDTH);

            grid.add(saveButton, 2, row+4);

            final Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().add("alert");
            deleteButton.setPrefWidth(BUTTON_WIDTH);

            grid.add(deleteButton, 2, row+5);

            // CALLBACKS
            updateIndexButton.setOnAction((event) -> {
                if (dirty) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Configuration not saved");
                    alert.setContentText("Configuration must be saved before updating index.");
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirmation");
                    alert.setHeaderText("Update index");
                    alert.setContentText("Are you sure you want to load this configuration\n" +
                        "and update the index?");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        catalog.get().loadConfig(config.getName());
                        catalog.get().updateIndex();
                        hide();
                    }
                }
            });

            clearIndexButton.setOnAction((event) -> {
                if (dirty) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Configuration not saved");
                    alert.setContentText("Configuration must be saved before clearing index.");
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirmation");
                    alert.setHeaderText("Clear index");
                    alert.setContentText("Are you sure you want to clear the index?");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        config.clearIndex();
                        drawCenterPane(border);
                    }
                }
            });

            saveButton.setOnAction((event) -> saveConfig(border));

            deleteButton.setOnAction((event) -> {
                Config candidate = config;   // avoid race conditions
                // confim delete
                if (catalog.get().getConfigName().equals(candidate.getName())) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Current configuration");
                    alert.setContentText("Cannot delete current configuration.");
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirmation");
                    alert.setHeaderText("Delete configuration");
                    alert.setContentText("Are you sure you want to delete this configuration?\n" +
                                         "This operation cannot be undone.");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // config is not guaranteed to be == candidate or != null, so do not use
                        listView.getItems().remove(candidate.getName());
                        listView.getSelectionModel().clearSelection();
                        candidate.delete();
                        candidate = null;
                    }
                }
            });
        } else {
            final Label label = new Label("No configuration selected");
            grid.add(label, 0, 0);
        }

        border.setCenter(grid);
    }

    private void saveConfig(BorderPane border) {
        // saving is potentially expensive: confirm with user before saving
        if (config == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No configuration selected");
            alert.setContentText("");
            alert.showAndWait();
            return;
        } else if (config.getLastUpdated() >= Config.INDEX_UPDATE_FAILED) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText("Saving invalidates index");
            alert.setContentText("Index will have to be rebuilt. Do you still want to save?");
            Optional<ButtonType> result = alert.showAndWait();
            if (!result.isPresent() || result.get() != ButtonType.OK) {
                return;
            }
        }
        // actually save
        for (Map.Entry<String,ComboBox<String>> e : optionsMap.entrySet()) {
            String value = e.getValue().getValue();
            if (value != null) {
                config.set(e.getKey(), value);
            }
        }
        dirty = false;
        drawCenterPane(border);
    }

    void show() {
        if (catalog.get().isIndexing()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText("Indexing in progress");
            alert.setContentText("Changing configuration will cancel indexing. Continue?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                catalog.get().cancelAllTasks();
            } else {    // cancel
                return;
            }
        }
        // select current config
        String configName = catalog.get().getConfigName();
        if (configName != null) {
            int index = Collections.binarySearch(listView.getItems(), configName);
            if (index >= 0) {
                listView.getSelectionModel().select(index);
            }
        }
        stage.show();
    }

    void hide() {
        listView.getSelectionModel().clearSelection();
        stage.hide();
    }

    ObjectProperty<Catalog> catalogProperty() { return catalog; }
}
