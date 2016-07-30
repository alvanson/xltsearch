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
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.UAX29URLEmailAnalyzer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;

class Config {
    public static final String XLT_VERSION = "0.0.1";

    public static final long INDEX_UPDATE_FAILED = -1;
    public static final long INDEX_NEVER_CREATED = -2;
    public static final long INDEX_INVALIDATED = -3;

    private static final String CONFIG_FILE = "config";
    private static final String CONFIG_COMMENT = "XLTSearch Index Configuration";
    private static final String CONFIG_DEFAULTS = "/config.defaults";
    // hash.algorithm
    private static final Map<String,String> HASH_ALGORITHM =
        Collections.unmodifiableMap(new LinkedHashMap<String,String>() {{
            put("MD5", "MD5");
            put("SHA-1", "SHA-1");
            put("SHA-256", "SHA-256");
        }});
    // lucene.version
    private static final Map<String,Version> LUCENE_VERSION =
        Collections.unmodifiableMap(new LinkedHashMap<String,Version>() {{
            put("4.6", Version.LUCENE_46);
        }});
    // lucene.analyzer
    private static final Map<String,Function<Version,Analyzer>> LUCENE_ANALYZER =
        Collections.unmodifiableMap(new LinkedHashMap<String,Function<Version,Analyzer>>() {{
            put("Standard", (v) -> new StandardAnalyzer(v));
            put("UAX29URLEmail", (v) -> new UAX29URLEmailAnalyzer(v));
            put("English", (v) -> new EnglishAnalyzer(v));
        }});
    // directory.type
    private static final Map<String,Function<File,Directory>> DIRECTORY_TYPE =
        Collections.unmodifiableMap(new LinkedHashMap<String,Function<File,Directory>>() {{
            put("FS", (f) -> {
                try { return FSDirectory.open(f); }
                catch (IOException ex) { ex.printStackTrace(); return null; }
            });
            put("RAM", (f) -> new RAMDirectory());
        }});
    // index.fields
    private static final Map<String,Supplier<IndexFields>> INDEX_FIELDS =
        Collections.unmodifiableMap(new LinkedHashMap<String,Supplier<IndexFields>>() {{
            put("Standard", IndexFields::new);
        }});
    // property map
    private static final Map<String,Map> PROPERTY_MAP =
        Collections.unmodifiableMap(new LinkedHashMap<String,Map>() {{
            put("hash.algorithm", HASH_ALGORITHM);
            put("lucene.version", LUCENE_VERSION);
            put("lucene.analyzer", LUCENE_ANALYZER);
            put("directory.type", DIRECTORY_TYPE);
            put("index.fields", INDEX_FIELDS);
        }});
    // other files
    private static final String HASH_SUMS_FILE = "hashsums";
    private static final String INDEX_DIR = "index";

    private final File configDir;
    private final String name;
    private final PersistentProperties properties;

    private final ReadOnlyListWrapper<Message> messages =
        new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    Config(File configDir, String name) {
        this.configDir = configDir;
        this.name = name;
        this.properties = new PersistentProperties(
            new File(configDir.getPath() + File.separator + CONFIG_FILE),
            CONFIG_COMMENT, getClass().getResourceAsStream(CONFIG_DEFAULTS));
        // check validitiy of index
        if (getLastUpdated() == INDEX_NEVER_CREATED && getHashSumsFile().exists()) {
            invalidateIndex();
        }
    }

    Set<String> getPropertyNames() {
        return PROPERTY_MAP.keySet();
    }

    Set<String> getOptions(String propertyName) {
        Set<String> options = null;
        if (PROPERTY_MAP.containsKey(propertyName)) {
            options = PROPERTY_MAP.get(propertyName).keySet();
        } else {
            addMessage(Message.Level.ERROR, "Unrecognized property name", propertyName);
        }
        return options;
    }

    String getValue(String propertyName) {
        return properties.getProperty(propertyName);
    }

    String getIndexMessage() {
        StringBuilder sb = new StringBuilder();
        long lastUpdated = getLastUpdated();
        if (lastUpdated >= 0) {
            sb.append("Last updated " + new Date(lastUpdated).toString());
        } else if (lastUpdated == INDEX_UPDATE_FAILED) {
            sb.append("Last update failed");
        } else if (lastUpdated == INDEX_NEVER_CREATED) {
            sb.append("Not yet created");
        } else {
            sb.append("Index invalidated");
        }
        return sb.toString();
    }

    long getLastUpdated() {
        long lastUpdated = INDEX_NEVER_CREATED;
        String lastUpdatedStr = properties.getProperty("last.updated");
        if (lastUpdatedStr != null) {
            try {
                lastUpdated = Long.parseLong(lastUpdatedStr);
            } catch (NumberFormatException ex) {
                addMessage(Message.Level.ERROR,
                    "Unable to determine time of last update", lastUpdatedStr);
                lastUpdated = INDEX_INVALIDATED;
                invalidateIndex();
            }
        }
        return lastUpdated;
    }

    // returns object corresponding to current value for `propertyName`
    <T> T get(String propertyName) {
        T t = null;
        if (PROPERTY_MAP.containsKey(propertyName)) {
            String option = properties.getProperty(propertyName);
            if (option != null) {
                t = (T) PROPERTY_MAP.get(propertyName).get(option);
                if (t == null) {
                    addMessage(Message.Level.ERROR,
                        "Unrecognized option for " + propertyName, option);
                }
            } else {
                addMessage(Message.Level.ERROR, "No default for property", propertyName);
            }
        } else {
            addMessage(Message.Level.ERROR, "Unrecognized property name", propertyName);
        }
        return t;
    }

    File getHashSumsFile() {
        return new File(configDir.getPath() + File.separator + HASH_SUMS_FILE);
    }

    File getIndexDir() {
        return new File(configDir.getPath() + File.separator + INDEX_DIR);
    }

    void set(String propertyName, String value) {
        // any change to properties (other than last updated) invalidates current index
        if (PROPERTY_MAP.containsKey(propertyName) && !propertyName.equals("last.updated")) {
            invalidateIndex();
        }
        properties.setProperty(propertyName, value);
    }

    void setLastUpdated(long value) {
        set("last.updated", Long.toString(value));
    }

    void invalidateIndex() {
        if (!Long.toString(INDEX_NEVER_CREATED).equals(properties.getProperty("last.updated"))) {
            set("last.updated", Long.toString(INDEX_INVALIDATED));
        }
    }

    void clearIndex() {
        try {
            deltree(getIndexDir());
            Files.deleteIfExists(getHashSumsFile().toPath());
            set("last.updated", Long.toString(INDEX_NEVER_CREATED));
        } catch (IOException ex) {
            addMessage(Message.Level.ERROR, "Could not clear index", getStackTrace(ex));
        }
    }

    void delete() {
        try {
            deltree(configDir);
        } catch (IOException ex) {
            addMessage(Message.Level.ERROR, "Could not delete configuration", getStackTrace(ex));
        }
        // CAUTION! Behaviour of this object after delete() is undefined!
    }

    private void deltree(File dir) throws IOException {
        if (dir.exists()) {
            // deltree/rm -r, does not follow symlinks I'm told
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
               }
            });
        }
    }

    private void addMessage(Message.Level level, String title, String details) {
        messages.get().add(new Message(getClass().getSimpleName(), level, title, details));
    }

    private final String getStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    String getName() { return name; }
    boolean isPersistent() { return properties.isPersistent(); }
    ReadOnlyListProperty<Message> messagesProperty() { return messages.getReadOnlyProperty(); }
}
