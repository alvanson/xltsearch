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

import java.util.LinkedList;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

class MessageDisplay {
    private static final int SCENE_WIDTH = 640;
    private static final int SCENE_HEIGHT = 480;

    private final Stage stage = new Stage();
    private final SimpleListProperty<Message> messages =
        new SimpleListProperty<>(FXCollections.observableList(new LinkedList<>()));

    MessageDisplay() {
        initUI();
    }

    private void initUI() {
        stage.setTitle("Messages");

        final SplitPane sp = new SplitPane();
        sp.setOrientation(Orientation.VERTICAL);

        final TableView<Message> table = new TableView<>();
        final TableColumn<Message,String> fromCol = new TableColumn<>("From");
        final TableColumn<Message,String> levelCol = new TableColumn<>("Level");
        final TableColumn<Message,String> summaryCol = new TableColumn<>("Summary");
        table.getColumns().addAll(fromCol, levelCol, summaryCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        final TextArea detailsField = new TextArea();
        detailsField.setMaxHeight(Double.MAX_VALUE);
        detailsField.setEditable(false);

        sp.getItems().addAll(table, detailsField);
        sp.setDividerPositions(2.0/3.0);

        table.itemsProperty().bind(messages);

        fromCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().from));
        levelCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().level.toString()));
        summaryCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().summary));

        table.getSelectionModel().selectedItemProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                detailsField.setText(newValue.details);
            } else {
                detailsField.setText("");
            }
        });

        table.setOnKeyPressed((event) -> {
            Message msg = table.getSelectionModel().getSelectedItem();
            if (msg != null && event.getCode() == KeyCode.DELETE) {
                messages.get().remove(msg);
            }
        });

        Scene scene = new Scene(sp, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add("stylesheet.css");
        stage.setScene(scene);
    }

    void show() {
        stage.show();
    }

    ListProperty<Message> messagesProperty() { return messages; }
}
