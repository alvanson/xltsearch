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

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

class DetailedAlert extends Alert {
    DetailedAlert(Alert.AlertType alertType) {
        super(alertType);
    }

    DetailedAlert(Alert.AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons);
    }

    void setDetailsText(String details) {
        GridPane content = new GridPane();
        content.setMaxHeight(Double.MAX_VALUE);

        Label label = new Label("Details:");
        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        content.add(label, 0, 0);
        content.add(textArea, 0, 1);

        this.getDialogPane().setExpandableContent(content);
        // work around DialogPane expandable content resize bug
        this.getDialogPane().expandedProperty().addListener((ov, oldValue, newValue) -> {
            Platform.runLater(() -> {
                this.getDialogPane().requestLayout();
                this.getDialogPane().getScene().getWindow().sizeToScene();
            });
        });
    }
}
