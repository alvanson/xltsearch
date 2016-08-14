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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;

class Catalog {
    static final String CATALOG_DIR = ".xltstore";

    private final File root;
    private boolean validConfig;
    private Config config;
    private String hashAlgorithm;
    private Version version;
    private Analyzer analyzer;
    private Similarity similarity;
    private Directory directory;
    private IndexFields indexFields;
    private long indexStart;  // -1 == not currently indexing

    private SelectTask selectTask;
    private ParseTask parseTask;
    private IndexTask indexTask;
    private SearchTask searchTask;

    private final ReadOnlyStringWrapper indexDetails = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper indexMessage = new ReadOnlyStringWrapper();
    private final ReadOnlyDoubleWrapper indexProgress = new ReadOnlyDoubleWrapper();
    private final ReadOnlyStringWrapper searchDetails = new ReadOnlyStringWrapper();
    private final ReadOnlyListWrapper<SearchResult> searchResults =
        new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    private final Logger logger = LoggerFactory.getLogger(Catalog.class);

    Catalog(File root) {
        this.root = root;
        this.validConfig = false;
        close();
    }

    List<String> getConfigs() {
        List<String> configs = new ArrayList<>();
        File dir = new File(root.getPath() + File.separator + CATALOG_DIR);
        if (dir.exists()) {
            for (String name : dir.list()) {
                File file = new File(dir.getPath() + File.separator + name);
                if (file.isDirectory()) {
                    configs.add(name);
                }
            }
        }
        return configs;
    }

    Config getConfig(String name) {
        if (name == null) {
            return null;
        }
        File configDir = new File(
            root.getPath() + File.separator + CATALOG_DIR + File.separator + name);
        // ensure directory `<root>/.xltstore/<name>/` exists
        if (!configDir.isDirectory()) {
            configDir.mkdirs();
        }
        return new Config(configDir, name);
    }

    void open(String name) {
        close();
        if (name != null) {
            loadConfig(name);
            clearMessages();
        }
    }

    private void loadConfig(String name) {
        // read configuration
        config = getConfig(name);   // will create config if !exists
        if (config.getLastUpdated() == Config.INDEX_INVALIDATED) { return; }
        // hashAlgorithm
        hashAlgorithm = config.get("hash.algorithm");
        if (hashAlgorithm == null) { return; }
        // version
        version = config.get("lucene.version");
        if (version == null) { return; }
        // analyzer
        Function<Version,Analyzer> analyzerFactory = config.get("lucene.analyzer");
        if (analyzerFactory == null) { return; }
        analyzer = analyzerFactory.apply(version);
        // similarity
        Supplier<Similarity> similarityFactory = config.get("scoring.model");
        if (similarityFactory == null) { return; }
        similarity = similarityFactory.get();
        // indexFields
        Supplier<IndexFields> indexFieldsFactory = config.get("index.fields");
        if (indexFieldsFactory == null) { return; }
        indexFields = indexFieldsFactory.get();
        // directory
        Function<File,Directory> directoryFactory = config.get("directory.type");
        if (directoryFactory == null) { return; }
        directory = directoryFactory.apply(config.getIndexDir());
        if (directory == null) { return; }
        // we made it: config is valid
        validConfig = true;
    }

    void updateIndex() {
        if (!validConfig) {
            logger.error("Cannot update index: invalid configuration");
            return;
        }
        cancelAllTasks();
        indexStart = System.currentTimeMillis();
        // set last.updated (temporarily) to UPDATE_FAILED in event of crash
        config.setLastUpdated(Config.INDEX_UPDATE_FAILED);
        // initialize queues
        BlockingQueue<Docket> parseQueue = new ArrayBlockingQueue<>(1); // lean queue
        BlockingQueue<Docket> indexQueue = new ArrayBlockingQueue<>(1); // lean queue
        // initalize tasks
        selectTask = new SelectTask(root, hashAlgorithm, directory, indexFields, parseQueue);
        parseTask = new ParseTask(root, parseQueue, indexQueue);
        indexTask = new IndexTask(indexQueue, version, analyzer, similarity, directory,
            indexFields);
        // communicate progress (use parseTask for current file, indexTask for %)
        parseTask.messageProperty().addListener((o, oldValue, newValue) -> updateIndexStatus());
        indexProgress.bind(indexTask.progressProperty());
        indexTask.setOnSucceeded((event) -> {
            if (indexTask.getValue() && parseTask.getValue() && selectTask.getValue()) {
                // everything worked
                config.setLastUpdated(indexStart);
            }   // else: index already marked INDEX_UPDATE_FAILED
            indexStart = -1;
            clearMessages();
        });
        // start threads
        startTask(selectTask);
        startTask(parseTask);
        startTask(indexTask);
    }

    private void updateIndexDetails() {
        if (config == null) {
            indexDetails.set("No configuration loaded");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(config.getName());
            sb.append("]: ");
            sb.append(config.getValue("directory.type"));
            sb.append(" / Lucene ");
            sb.append(config.getValue("lucene.version"));
            sb.append(" / Analyzer: ");
            sb.append(config.getValue("lucene.analyzer"));
            sb.append(" / Scoring: ");
            sb.append(config.getValue("scoring.model"));
            indexDetails.set(sb.toString());
        }
    }

    private void updateIndexStatus() {
        if (config == null) {
            indexMessage.set("");
            indexProgress.unbind();
            indexProgress.set(0);
        } else if (isIndexing() && parseTask != null) {
            indexMessage.set(String.format("%.0f%%, processing %s",
                Math.max(Math.floor(indexProgress.get()*100), 0),   // avoid -%
                parseTask.messageProperty().get()));
        } else {    // no longer updating
            indexMessage.set(config.getIndexStatus());
            indexProgress.unbind(); // not/no longer updating
            if (config.getLastUpdated() >= 0) {
                indexProgress.set(1);   // no error
            } else {
                indexProgress.set(0);   // something is wrong
            }
        }
    }

    void search(String query, int limit) {
        if (!validConfig) {
            logger.error("Cannot perform search: invalid configuration");
            return;
        }
        // clear existing search results
        searchResults.get().clear();
        // cancel existing search task (if any)
        if (searchTask != null) {
            searchTask.cancel();
        }
        // initalize task
        searchTask = new SearchTask(root, version, analyzer, similarity, directory, indexFields, query, limit);
        searchDetails.bind(searchTask.messageProperty());
        searchTask.setOnSucceeded((event) -> {
            // populate search results
            List<SearchResult> results = searchTask.getValue();
            if (results != null) {
                searchResults.get().addAll(results);
            }
        });
        startTask(searchTask);
    }

    void openFile(File file) {
        startTask(new OpenFileTask(file));
    }

    private <V> void startTask(Task<V> task) {
        // start task
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    void cancelAllTasks() {
        if (searchTask != null) {
            searchTask.cancel();
        }
        if (indexTask != null) {
            indexTask.cancel();
        }
        if (parseTask != null) {
            parseTask.cancel();
        }
        if (selectTask != null) {
            selectTask.cancel();
        }
        indexStart = -1;
        clearMessages();
    }

    void clearMessages() {
        updateIndexDetails();
        updateIndexStatus();
        searchDetails.unbind();
        searchDetails.set("");
        searchResults.get().clear();
    }

    void close() {
        cancelAllTasks();
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException ex) {
                logger.error("I/O exception while closing index", ex);
            }
        }
        directory = null;
        validConfig = false;
    }

    String getConfigName() {
        if (config != null) {
            return config.getName();
        } else {
            return null;
        }
    }
    String getPath() {
        return root.getPath();
    }
    boolean isIndexing() {
        return indexStart >= 0;
    }
    ReadOnlyStringProperty indexDetailsProperty() {
        return indexDetails.getReadOnlyProperty();
    }
    ReadOnlyStringProperty indexMessageProperty() {
        return indexMessage.getReadOnlyProperty();
    }
    ReadOnlyDoubleProperty indexProgressProperty() {
        return indexProgress.getReadOnlyProperty();
    }
    ReadOnlyStringProperty searchDetailsProperty() {
        return searchDetails.getReadOnlyProperty();
    }
    ReadOnlyListProperty<SearchResult> searchResultsProperty() {
        return searchResults.getReadOnlyProperty();
    }
}
