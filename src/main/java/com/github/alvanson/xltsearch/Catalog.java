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
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import javafx.collections.ListChangeListener;

class Catalog {
    private static final String CATALOG_DIR = ".xltstore";
    private static final String HASH_SUMS_COMMENT = "XLTSearch Hash Sums";

    private final File root;
    private boolean validConfig;
    private Config config;
    private Version version;
    private Analyzer analyzer;
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
    private final ReadOnlyListWrapper<Message> messages =
        new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    Catalog(File root) {
        this.root = root;
        this.validConfig = false;
        indexStart = -1;
        updateIndexDetails();
        updateIndexStatus();
        searchDetails.set("");
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
        Config result = new Config(configDir, name);
        // check for persistence
        if (!result.isPersistent()) {
            addMessage(Message.Level.ERROR,
                "I/O exception while opening configuration",
                "Unable to persist configuration. Settings will not be saved.");
        }
        // Catalog is responsible for listening to all objects created by it
        result.messagesProperty().addListener(
                (ListChangeListener.Change<? extends Message> c) -> {
            while (c.next()) {
                for (Message msg : c.getAddedSubList()) {
                    messages.get().add(msg);
                }
            }
        });
        return result;
    }

    void loadConfig(String name) {
        close();
        validConfig = false;
        if (name != null) {
            config = getConfig(name);   // will create config if !exists
            // read configuration
            if (config.getLastUpdated() > Config.INDEX_INVALIDATED) {
                version = config.get("lucene.version");
                if (version != null) {
                    Function<Version,Analyzer> analyzerFactory = config.get("lucene.analyzer");
                    if (analyzerFactory != null) {
                        analyzer = analyzerFactory.apply(version);
                        Supplier<IndexFields> indexFieldsFactory = config.get("index.fields");
                        if (indexFieldsFactory != null) {
                            indexFields = indexFieldsFactory.get();
                            Function<File,Directory> directoryFactory =
                                config.get("directory.type");
                            if (directoryFactory != null) {
                                directory = directoryFactory.apply(config.getIndexDir());
                                // functional interface returns null instead of IOException
                                if (directory != null) {
                                    validConfig = true;
                                } else {
                                    addMessage(Message.Level.ERROR,
                                        "I/O exception while opening index", "");
                                }
                            }
                        }
                    }
                }
            }
        }
        // blank all messages
        indexStart = -1;
        updateIndexDetails();
        updateIndexStatus();
        searchDetails.set("");
        searchResults.get().clear();
    }

    // return list of all files (recursively) under root as relative paths
    private List<String> listFiles() { return listFiles(""); }
    // caller must ensure that rel contains trailing separator
    private List<String> listFiles(String rel) {
        List<String> files = new ArrayList<>();
        // build absolute path name
        String abs = root.getPath() + File.separator + rel;
        // iterate through each file and directory in `<root>/rel`
        File dir = new File(abs);
        for (String name : dir.list()) {
            if (!name.equals(CATALOG_DIR)) {  // don't index the catalog
                File file = new File(abs + name);
                if (file.isDirectory()) {
                    files.addAll(listFiles(rel + name + File.separator));
                } else {
                    files.add(rel + name);
                }
            }
        }
        return files;
    }

    void updateIndex() {
        if (!validConfig) {
            addMessage(Message.Level.ERROR, "Invalid configuration", "Cannot update index.");
            return;
        }
        cancelAllTasks();
        indexStart = System.currentTimeMillis();
        // set last.updated (temporarily) to UPDATE_FAILED in event of crash
        config.setLastUpdated(Config.INDEX_UPDATE_FAILED);
        // recursively list all files in folder
        List<String> files = listFiles();
        int n = files.size();
        // load hash sums
        Properties hashSums = new Properties();
        final File hashSumsFile = config.getHashSumsFile();
        long lastUpdated = config.getLastUpdated();
        if (hashSumsFile.exists()) {
            try (FileInputStream in = new FileInputStream(hashSumsFile)) {
                hashSums.load(in);
            } catch (IOException ex) {
                addMessage(Message.Level.ERROR, "I/O exception while loading hash sums",
                    "Unable to load file checksums. Entire index will be rebuilt.");
            }
        }
        // convert Properties to proper Map<String,String>
        Map<String,String> hashSumMap = new HashMap<>();
        for (String relPath : hashSums.stringPropertyNames()) {
            hashSumMap.put(relPath, hashSums.getProperty(relPath));
        }
        // initialize queues
        BlockingQueue<Docket> parseQueue = new ArrayBlockingQueue<>(1); // lean queue
        BlockingQueue<Docket> indexQueue = new ArrayBlockingQueue<>(1); // lean queue
        // initalize tasks
        selectTask = new SelectTask(root, files, config.get("hash.algorithm"),
            Collections.unmodifiableMap(hashSumMap), parseQueue, n);
        parseTask = new ParseTask(root, parseQueue, indexQueue, n);
        indexTask = new IndexTask(indexQueue, version, analyzer, directory, indexFields, n);
        // communicate progress (use parseTask for current file, indexTask for %)
        parseTask.messageProperty().addListener((o, oldValue, newValue) -> updateIndexStatus());
        indexProgress.bind(indexTask.progressProperty());
        indexTask.setOnSucceeded((event) -> {
            if (indexTask.getValue() != null && parseTask.getValue() && selectTask.getValue()) {
                Properties newHashSums = new Properties();
                for (Map.Entry<String,String> e : indexTask.getValue().entrySet()) {
                    newHashSums.setProperty(e.getKey(), e.getValue());
                }
                try (FileOutputStream out = new FileOutputStream(hashSumsFile)) {
                    newHashSums.store(out, HASH_SUMS_COMMENT);
                    config.setLastUpdated(indexStart);  // everything worked
                } catch (IOException ex) {
                    addMessage(Message.Level.ERROR, "I/O exception while saving hash sums",
                        "Unable to save file checksums.");
                }
            }   // else/catch: index already marked INDEX_UPDATE_FAILED
            indexStart = -1;
            updateIndexDetails();
            updateIndexStatus();
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
            sb.append(" / Index Fields: ");
            sb.append(config.getValue("index.fields"));
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
            indexMessage.set(config.getIndexMessage());
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
            addMessage(Message.Level.ERROR, "Invalid configuration", "Cannot perform search.");
            return;
        }
        // clear existing search results
        searchResults.get().clear();
        // cancel existing search task (if any)
        if (searchTask != null) {
            searchTask.cancel();
        }
        // initalize task
        searchTask = new SearchTask(root, version, analyzer, directory, indexFields, query, limit);
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

    private <V> void startTask(BaseTask<V> task) {
        // add new messages to Catalog messages list
        task.messagesProperty().addListener((ListChangeListener.Change<? extends Message> c) -> {
            while (c.next()) {
                for (Message msg : c.getAddedSubList()) {
                    messages.get().add(msg);
                }
            }
        });
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
        // update status
        indexStart = -1;
        updateIndexDetails();
        updateIndexStatus();
    }

    void close() {
        cancelAllTasks();
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        directory = null;
    }

    private void addMessage(Message.Level level, String summary, String details) {
        messages.get().add(new Message(getClass().getSimpleName(), level, summary, details));
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
    ReadOnlyListProperty<Message> messagesProperty() {
        return messages.getReadOnlyProperty();
    }
}
