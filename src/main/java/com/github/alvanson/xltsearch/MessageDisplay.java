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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

class MessageDisplay {
    private static final int SCENE_WIDTH = 640;
    private static final int SCENE_HEIGHT = 480;

    @FXML private TableView<Message> table;
    @FXML private TableColumn<Message,String> fromCol;
    @FXML private TableColumn<Message,String> levelCol;
    @FXML private TableColumn<Message,String> summaryCol;
    @FXML private TextArea detailsField;

    private final Stage stage = new Stage();
    private final SimpleListProperty<Message> messages =
        new SimpleListProperty<>(FXCollections.observableList(new LinkedList<>()));

    MessageDisplay() {
        stage.setTitle("Messages");

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/message_display.fxml"));
        fxmlLoader.setController(this);
        try {
            Scene scene = new Scene(fxmlLoader.load(), SCENE_WIDTH, SCENE_HEIGHT);
            scene.getStylesheets().add("stylesheet.css");
            stage.setScene(scene);
        } catch (Exception ex) {
            DetailedAlert alert = new DetailedAlert(DetailedAlert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Exception while loading MessageDisplay");
            alert.setDetailsText(Message.getStackTrace(ex));
            alert.showAndWait();
        }
    }

    @FXML
    private void initialize() {
        // replaces CONSTRAINED_RESIZE_POLICY
        summaryCol.prefWidthProperty().bind(
            table.widthProperty()
            .subtract(fromCol.widthProperty())
            .subtract(levelCol.widthProperty())
        );

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
    }

    @FXML
    private void deleteMessage(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            Message msg = table.getSelectionModel().getSelectedItem();
            if (msg != null) {
                messages.get().remove(msg);
            }
        }
    }

    void show() {
        stage.show();
    }

    ListProperty<Message> messagesProperty() { return messages; }
}
