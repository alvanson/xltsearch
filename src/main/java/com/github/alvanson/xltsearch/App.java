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

import java.io.File;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class App extends Application {
    private static final String APP_CONFIG_FILE = ".xltsearch";
    private static final String APP_CONFIG_COMMENT = "XLTSearch App Configuration";
    private static final int DEFAULT_LIMIT = 100;

    private static final int SCENE_WIDTH = 640;
    private static final int SCENE_HEIGHT = 480;
    private static final int BUTTON_WIDTH = 100;

    private PersistentProperties properties;

    private final ObjectProperty<Catalog> catalog = new SimpleObjectProperty<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage stage) {
        // draw UI
        initUI(stage);
        // load application properties
        properties = new PersistentProperties(
            new File(System.getProperty("user.home") + File.separator + APP_CONFIG_FILE),
            APP_CONFIG_COMMENT, null);
        // alert user if `properties` is not persistent
        if (!properties.isPersistent()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Unable to persist application properties");
            alert.setContentText("Application settings will not be saved.");
            alert.showAndWait();
        }
        // open last folder with last config
        String lastFolder = properties.getProperty("last.folder");
        String lastConfig = properties.getProperty("last.config");  // ok if null
        // check for existence of last folder, prompt user if null/not found
        File dir = lastFolder == null ? null : new File(lastFolder);
        if (dir == null || !dir.isDirectory()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Welcome");
            alert.setHeaderText("Welcome to XLTSearch!");
            alert.setContentText("Please choose a folder to search.");
            alert.showAndWait();
            DirectoryChooser directoryChooser = new DirectoryChooser();
            dir = directoryChooser.showDialog(stage);
            if (dir == null) {
                // user cancelled "Open Folder"
                Platform.exit();
                return;
            }
        }
        // open folder and load config
        catalog.set(new Catalog(dir));
        catalog.get().loadConfig(lastConfig);
    }

    private void initUI(final Stage stage) {
        stage.setTitle("XLTSearch " + Config.XLT_VERSION);

        final BorderPane border = new BorderPane();

        // TOP
        final VBox topVBox = new VBox();

        final VBox toolVBox = new VBox(5);
        toolVBox.setPadding(new Insets(10));
        toolVBox.getStyleClass().add("info-bar");

        // folder line
        final HBox folderHBox = new HBox(10);
        folderHBox.setAlignment(Pos.BASELINE_LEFT);

        final Label folderLabel = new Label("Folder:");
        folderLabel.getStyleClass().addAll("info-bar", "caption");
        folderLabel.setMinWidth(Label.USE_PREF_SIZE);
        final Label folderPathLabel = new Label();
        folderPathLabel.getStyleClass().add("info-bar");
        folderPathLabel.setMaxWidth(Double.MAX_VALUE);
        final Button folderButton = new Button("Open Folder");
        folderButton.setPrefWidth(BUTTON_WIDTH);
        folderButton.setMinWidth(Button.USE_PREF_SIZE);

        folderHBox.getChildren().addAll(folderLabel, folderPathLabel, folderButton);
        folderHBox.setHgrow(folderPathLabel, Priority.ALWAYS);

        // index line
        final HBox indexHBox = new HBox(10);
        indexHBox.setAlignment(Pos.CENTER_LEFT);

        final Label indexLabel = new Label("Index:");
        indexLabel.getStyleClass().addAll("info-bar", "caption");
        indexLabel.setMinWidth(Label.USE_PREF_SIZE);
        final Label indexDetailsLabel = new Label();
        indexDetailsLabel.getStyleClass().add("info-bar");
        indexDetailsLabel.setMaxWidth(Double.MAX_VALUE);
        final Button configureButton = new Button("Configure");
        configureButton.setPrefWidth(BUTTON_WIDTH);
        configureButton.setMinWidth(Button.USE_PREF_SIZE);

        indexHBox.getChildren().addAll(indexLabel, indexDetailsLabel, configureButton);
        indexHBox.setHgrow(indexDetailsLabel, Priority.ALWAYS);

        toolVBox.getChildren().addAll(folderHBox, indexHBox);

        // search bar
        final GridPane searchGrid = new GridPane();
        searchGrid.setAlignment(Pos.BASELINE_LEFT);
        searchGrid.setHgap(20);
        searchGrid.setVgap(10);
        searchGrid.setPadding(new Insets(10));

        // left half: query and button
        final HBox leftSearchHBox = new HBox(10);
        leftSearchHBox.setAlignment(Pos.BASELINE_LEFT);

        final TextField queryField = new TextField();
        final Button searchButton = new Button("Search");

        leftSearchHBox.getChildren().addAll(queryField, searchButton);
        leftSearchHBox.setHgrow(queryField, Priority.ALWAYS);

        // right half: limit and status
        final HBox rightSearchHBox = new HBox(10);
        rightSearchHBox.setAlignment(Pos.BASELINE_LEFT);

        final Label limitLabel = new Label("Limit:");
        final TextField limitField = new TextField(Integer.toString(DEFAULT_LIMIT));
        limitField.setPrefColumnCount(4);
        final Label searchMessageLabel = new Label();
        searchMessageLabel.setTextAlignment(TextAlignment.CENTER);

        rightSearchHBox.getChildren().addAll(limitLabel, limitField, searchMessageLabel);
        rightSearchHBox.setHgrow(searchMessageLabel, Priority.ALWAYS);

        searchGrid.add(leftSearchHBox, 0, 0);
        searchGrid.add(rightSearchHBox, 1, 0);

        final ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50); // 50/50 column constraints
        searchGrid.getColumnConstraints().addAll(cc, cc);

        topVBox.getChildren().addAll(toolVBox, searchGrid);

        border.setTop(topVBox);

        // CENTER
        final SplitPane sp = new SplitPane();
        sp.setOrientation(Orientation.VERTICAL);

        final TableView<SearchResult> resultsTable = new TableView<>();
        final TableColumn<SearchResult,String> fileNameCol = new TableColumn<>("Filename");
        final TableColumn<SearchResult,String> titleCol = new TableColumn<>("Title");
        final TableColumn<SearchResult,String> scoreCol = new TableColumn<>("Score");
        resultsTable.getColumns().addAll(fileNameCol, titleCol, scoreCol);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        final TextArea detailsField = new TextArea();
        detailsField.setMaxHeight(Double.MAX_VALUE);
        detailsField.setEditable(false);

        sp.getItems().addAll(resultsTable, detailsField);
        sp.setDividerPositions(2.0/3.0);

        border.setCenter(sp);

        // BOTTOM
        final HBox statusHBox = new HBox(10);
        statusHBox.setAlignment(Pos.CENTER);
        statusHBox.setPadding(new Insets(5, 10, 5, 10));

        final Label statusLabel = new Label("Status:");
        statusLabel.setMinWidth(Label.USE_PREF_SIZE);
        statusLabel.getStyleClass().add("caption");
        final Label indexMessageLabel = new Label("");
        indexMessageLabel.setMaxWidth(Double.MAX_VALUE);
        final ProgressBar indexProgress = new ProgressBar();
        indexProgress.setPrefWidth(BUTTON_WIDTH);
        indexProgress.setMinWidth(ProgressBar.USE_PREF_SIZE);
        final Button messagesButton = new Button("Messages");
        messagesButton.setPrefWidth(BUTTON_WIDTH);
        messagesButton.setMinWidth(Button.USE_PREF_SIZE);

        statusHBox.getChildren().addAll(
            statusLabel, indexMessageLabel, indexProgress, messagesButton);
        statusHBox.setHgrow(indexMessageLabel, Priority.ALWAYS);

        border.setBottom(statusHBox);

        // DIALOGS
        final Configurator configurator = new Configurator();
        final MessageDisplay messageDisplay = new MessageDisplay();

        // UI BINDINGS
        configurator.catalogProperty().bind(catalog);
        catalog.addListener((o, oldValue, newValue) -> {
            folderPathLabel.setText(newValue.getPath());
            // bind information controls to new catalog
            indexDetailsLabel.textProperty().bind(newValue.indexDetailsProperty());
            searchMessageLabel.textProperty().bind(newValue.searchDetailsProperty());
            resultsTable.itemsProperty().bind(newValue.searchResultsProperty());
            indexMessageLabel.textProperty().bind(newValue.indexMessageProperty());
            indexProgress.progressProperty().bind(newValue.indexProgressProperty());
            // notify user of any existing (on load) messages
            notify(newValue.messagesProperty().get(), messagesButton);
            // user notification bindings
            newValue.messagesProperty().addListener(
                    (ListChangeListener.Change<? extends Message> c) -> {
                while (c.next()) {
                    notify(c.getAddedSubList(), messagesButton);
                }
            });
            messageDisplay.messagesProperty().bind(newValue.messagesProperty());
        });

        fileNameCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().file.getName()));
        titleCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().title));
        scoreCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(String.format("%.0f", r.getValue().score*100)));

        // CALLBACKS
        folderButton.setOnAction((event) -> {
            if (catalog.get().isIndexing()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation");
                alert.setHeaderText("Indexing in progress");
                alert.setContentText("Opening a new folder will cancel indexing. Continue?");
                Optional<ButtonType> result = alert.showAndWait();
                // escape on cancel/close
                if (!result.isPresent() || result.get() != ButtonType.OK) {
                    return;
                }
            }
            DirectoryChooser directoryChooser = new DirectoryChooser();
            File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                if (catalog.get() != null) {
                    catalog.get().close();
                }
                catalog.set(new Catalog(dir));
            }  // do nothing on cancel
        });

        configureButton.setOnAction((event) -> configurator.show());

        queryField.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.ENTER) {
                search(queryField, limitField, detailsField);
            }
        });

        searchButton.defaultButtonProperty().bind(searchButton.focusedProperty());
        searchButton.setOnAction((event) -> search(queryField, limitField, detailsField));

        limitField.setOnKeyPressed((event) -> {
            if (event.getCode() == KeyCode.ENTER) {
                search(queryField, limitField, detailsField);
            }
        });

        resultsTable.getSelectionModel().selectedItemProperty().addListener(
                (o, oldValue, newValue) -> {
            if (newValue != null) {
                detailsField.setText(newValue.details);
            } else {
                detailsField.setText("");
            }
        });

        resultsTable.setOnKeyPressed((event) -> {
            SearchResult result = resultsTable.getSelectionModel().getSelectedItem();
            if (result != null && event.getCode() == KeyCode.ENTER) {
                catalog.get().openFile(result.file);
            }
        });

        resultsTable.setRowFactory((tv) -> {
            final TableRow<SearchResult> row = new TableRow<>();
            row.setOnMouseClicked((event) -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    catalog.get().openFile(row.getItem().file);
                }
            });
            return row;
        });

        messagesButton.setOnAction((event) -> {
            messagesButton.getStyleClass().remove("alert");
            messageDisplay.show();
        });

        Scene scene = new Scene(border, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add("stylesheet.css");
        stage.setScene(scene);
        stage.show();
    }

    // validate input and execute search
    private void search(TextField queryField, TextField limitField, TextArea detailsField) {
        // validate limit field
        String limitStr = limitField.getText();
        limitStr = limitStr.replaceAll("[^\\d]","");
        if (limitStr.equals("")) {
            limitStr = Integer.toString(DEFAULT_LIMIT);
        }
        limitField.setText(limitStr);
        // results table clears automatically
        detailsField.setText("");
        // execute search
        int limit = Integer.parseInt(limitStr);
        catalog.get().search(queryField.getText(), limit);
    }

    // handle messages received on messagesProperty
    private void notify(List<? extends Message> messages, Button button) {
        // immediately display errors
        for (Message msg : messages) {
            if (msg.level.compareTo(Message.Level.ERROR) >= 0) {
                DetailedAlert alert = new DetailedAlert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(msg.summary);
                alert.setDetailsText(msg.details);
                alert.showAndWait();
            }
        }
        // restyle button to indicate that new messages are available
        if (messages.size() > 0 && !button.getStyleClass().contains("alert")) {
            button.getStyleClass().add("alert");
        }
    }

    @Override
    public void stop() {
        // save last folder and config, close catalog
        Catalog c = catalog.get();
        if (c != null) {
            properties.setProperty("last.folder", c.getPath());
            String configName = c.getConfigName();
            if (configName != null) {
                properties.setProperty("last.config", configName);
            } else {
                properties.remove("last.config");
            }
            c.close();
        }
    }
}
