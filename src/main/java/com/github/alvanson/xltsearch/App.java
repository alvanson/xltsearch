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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

public class App extends Application {
    private static final String APP_CONFIG_FILE = ".xltsearch";
    private static final String APP_CONFIG_COMMENT = "XLTSearch App Configuration";
    private static final int DEFAULT_LIMIT = 100;

    private static final int SCENE_WIDTH = 640;
    private static final int SCENE_HEIGHT = 480;

    @FXML private Label folderPathLabel;
    @FXML private Label indexDetailsLabel;
    @FXML private TextField queryField;
    @FXML private Button searchButton;
    @FXML private TextField limitField;
    @FXML private Label searchMessageLabel;
    @FXML private TableView<SearchResult> resultsTable;
    @FXML private TableColumn<SearchResult,String> fileNameCol;
    @FXML private TableColumn<SearchResult,String> titleCol;
    @FXML private TableColumn<SearchResult,String> scoreCol;
    @FXML private TextArea detailsField;
    @FXML private Label indexMessageLabel;
    @FXML private ProgressBar indexProgress;
    @FXML private Button messagesButton;

    private Stage stage;
    private Configurator configurator;
    private MessageDisplay messageDisplay;
    private PersistentProperties properties;
    private final ObjectProperty<Catalog> catalog = new SimpleObjectProperty<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage stage) {
        this.stage = stage;
        // initialize UI
        stage.setTitle("XLTSearch " + Config.XLT_VERSION);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/app.fxml"));
        fxmlLoader.setController(this);
        try {
            Scene scene = new Scene(fxmlLoader.load(), SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
        } catch (Exception ex) {
            DetailedAlert alert = new DetailedAlert(DetailedAlert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Exception while loading App");
            alert.setDetailsText(MessageLogger.getStackTrace(ex));
            alert.showAndWait();
        }
        // error notification
        MessageLogger.messagesProperty().addListener(
                (ListChangeListener.Change<? extends Message> c) -> {
            while (c.next()) {
                notify(c.getAddedSubList());
            }
        });
        // load application properties
        properties = new PersistentProperties(
            new File(System.getProperty("user.home") + File.separator + APP_CONFIG_FILE),
            APP_CONFIG_COMMENT, null);
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

    @FXML
    private void initialize() {
        // replaces CONSTRAINED_RESIZE_POLICY
        scoreCol.prefWidthProperty().bind(
            resultsTable.widthProperty()
            .subtract(fileNameCol.widthProperty())
            .subtract(titleCol.widthProperty())
        );

        searchButton.defaultButtonProperty().bind(searchButton.focusedProperty());
        limitField.setText(Integer.toString(DEFAULT_LIMIT));

        // DIALOGS
        configurator = new Configurator();
        messageDisplay = new MessageDisplay();

        // UI BINDINGS
        configurator.catalogProperty().bind(catalog);
        catalog.addListener((o, oldValue, newValue) -> {
            folderPathLabel.setText(newValue.getPath());
            // bind information controls to new catalog
            indexDetailsLabel.textProperty().unbind();
            indexDetailsLabel.textProperty().bind(newValue.indexDetailsProperty());
            searchMessageLabel.textProperty().unbind();
            searchMessageLabel.textProperty().bind(newValue.searchDetailsProperty());
            resultsTable.itemsProperty().unbind();
            resultsTable.itemsProperty().bind(newValue.searchResultsProperty());
            indexMessageLabel.textProperty().unbind();
            indexMessageLabel.textProperty().bind(newValue.indexMessageProperty());
            indexProgress.progressProperty().unbind();
            indexProgress.progressProperty().bind(newValue.indexProgressProperty());
        });

        fileNameCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().file.getName()));
        titleCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(r.getValue().title));
        scoreCol.setCellValueFactory((r) ->
            new ReadOnlyStringWrapper(String.format("%.0f", r.getValue().score*100)));

        // CALLBACKS
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
            // open file on double-click
            row.setOnMouseClicked((event) -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    catalog.get().openFile(row.getItem().file);
                }
            });
            // enable drag-and-drop
            row.setOnDragDetected((event) -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.COPY, TransferMode.LINK);
                    ClipboardContent content = new ClipboardContent();
                    content.putFiles(Collections.singletonList(row.getItem().file));
                    db.setContent(content);
                    event.consume();
                }
            });
            return row;
        });
    }

    @FXML
    private void openFolder() {
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
    }

    @FXML
    private void configure() {
        configurator.show();
    }

    // validate input and execute search
    @FXML
    private void search() {
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

    @FXML
    private void searchOnEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            search();
        }
    }

    // handle messages received from MessageLogger
    private void notify(List<? extends Message> messages) {
        // immediately display errors
        for (Message msg : messages) {
            if (msg.level.compareTo(Message.Level.ERROR) >= 0) {
                DetailedAlert alert = new DetailedAlert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(msg.summary);
                alert.setDetailsText(msg.details);
                alert.show();   // showAndWait() causes repeats and race conditions
            }
        }
        // restyle button to indicate that new messages are available
        if (messages.size() > 0 && !messagesButton.getStyleClass().contains("alert")) {
            messagesButton.getStyleClass().add("alert");
        }
    }

    @FXML
    private void displayMessages() {
        messagesButton.getStyleClass().remove("alert");
        messageDisplay.show();
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
