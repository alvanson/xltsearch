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
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.UAX29URLEmailAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

class Config {
    static final long INDEX_UPDATE_FAILED = -1;
    static final long INDEX_NEVER_CREATED = -2;
    static final long INDEX_INVALIDATED = -3;

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
            put("Classic", (v) -> new ClassicAnalyzer(v));
            put("UAX29URLEmail", (v) -> new UAX29URLEmailAnalyzer(v));
            put("English", (v) -> new EnglishAnalyzer(v));
        }});
    // scoring.model
    private static final Map<String,Supplier<Similarity>> SCORING_MODEL =
        Collections.unmodifiableMap(new LinkedHashMap<String,Supplier<Similarity>>() {{
            put("Default", DefaultSimilarity::new);
            put("BM25", BM25Similarity::new);
        }});
    // directory.type
    private static final Map<String,Function<File,Directory>> DIRECTORY_TYPE =
        Collections.unmodifiableMap(new LinkedHashMap<String,Function<File,Directory>>() {{
            put("FS", (f) -> {
                Logger logger = LoggerFactory.getLogger(Config.class);
                try {
                    return FSDirectory.open(f);
                } catch (IOException ex) {
                    logger.error("I/O exception while opening index", f.getName(), ex);
                    return null;
                }
            });
            put("RAM", (f) -> new RAMDirectory());
        }});
    // property map
    private static final Map<String,Map> PROPERTY_MAP =
        Collections.unmodifiableMap(new LinkedHashMap<String,Map>() {{
            put("hash.algorithm", HASH_ALGORITHM);
            put("lucene.version", LUCENE_VERSION);
            put("lucene.analyzer", LUCENE_ANALYZER);
            put("scoring.model", SCORING_MODEL);
            put("directory.type", DIRECTORY_TYPE);
        }});
    private static final String INDEX_DIR = "index";

    // index fields
    final String contentField = "content";
    final Map<String,Property> metadataFields =
        Collections.unmodifiableMap(new HashMap<String,Property>() {{
            put("recipient", Property.internalText(Message.MESSAGE_RECIPIENT_ADDRESS));
            put("from", Property.internalText(Message.MESSAGE_FROM));
            put("to", Property.internalText(Message.MESSAGE_TO));
            put("cc", Property.internalText(Message.MESSAGE_CC));
            put("bcc", Property.internalText(Message.MESSAGE_BCC));
            put("format", TikaCoreProperties.FORMAT);
            put("identifier", TikaCoreProperties.IDENTIFIER);
            put("contributor", TikaCoreProperties.CONTRIBUTOR);
            put("coverage", TikaCoreProperties.COVERAGE);
            put("creator", TikaCoreProperties.CREATOR);
            put("modifier", TikaCoreProperties.MODIFIER);
            put("creatortool", TikaCoreProperties.CREATOR_TOOL);
            put("language", TikaCoreProperties.LANGUAGE);
            put("publisher", TikaCoreProperties.PUBLISHER);
            put("relation", TikaCoreProperties.RELATION);
            put("rights", TikaCoreProperties.RIGHTS);
            put("source", TikaCoreProperties.SOURCE);
            put("type", TikaCoreProperties.TYPE);
            put("title", TikaCoreProperties.TITLE);
            put("description", TikaCoreProperties.DESCRIPTION);
            put("keywords", TikaCoreProperties.KEYWORDS);
            put("created", TikaCoreProperties.CREATED);
            put("modified", TikaCoreProperties.MODIFIED);
            put("printdate", TikaCoreProperties.PRINT_DATE);
            put("metadatadate", TikaCoreProperties.METADATA_DATE);
            put("latitude", TikaCoreProperties.LATITUDE);
            put("longitude", TikaCoreProperties.LONGITUDE);
            put("altitude", TikaCoreProperties.ALTITUDE);
            put("rating", TikaCoreProperties.RATING);
            put("comments", TikaCoreProperties.COMMENTS);
        }});
    final String pathField = "path";
    final String titleField = "title";
    final String hashSumField = "hashsum";

    private final File configDir;
    private final String name;
    private final PersistentProperties properties;

    private boolean resolved = false;
    private String hashAlgorithm = null;
    private Version version = null;
    private Analyzer analyzer = null;
    private Similarity similarity = null;
    private Directory directory = null;

    private final Logger logger = LoggerFactory.getLogger(Config.class);

    Config(File configDir, String name) {
        this.configDir = configDir;
        this.name = name;
        this.properties = new PersistentProperties(
            new File(configDir.getPath() + File.separator + CONFIG_FILE),
            CONFIG_COMMENT, getClass().getResourceAsStream(CONFIG_DEFAULTS));
    }

    void resolve() {
        if (resolved) { return; }
        // else: resolved == false
        if (getLastUpdated() == INDEX_INVALIDATED) { return; }
        // hashAlgorithm
        hashAlgorithm = get("hash.algorithm");
        if (hashAlgorithm == null) { return; }
        // version
        version = get("lucene.version");
        if (version == null) { return; }
        // analyzer
        Function<Version,Analyzer> analyzerFactory = get("lucene.analyzer");
        if (analyzerFactory == null) { return; }
        analyzer = analyzerFactory.apply(version);
        // similarity
        Supplier<Similarity> similarityFactory = get("scoring.model");
        if (similarityFactory == null) { return; }
        similarity = similarityFactory.get();
        // directory
        Function<File,Directory> directoryFactory = get("directory.type");
        if (directoryFactory == null) { return; }
        directory = directoryFactory.apply(
            new File(configDir.getPath() + File.separator + INDEX_DIR));
        if (directory == null) { return; }
        // we made it: config is properly resolved
        resolved = true;
    }

    // returns object corresponding to current value for `propertyName`
    private <T> T get(String propertyName) {
        if (!PROPERTY_MAP.containsKey(propertyName)) {
            logger.error("Unrecognized property name {}", propertyName);
            return null;
        }
        // get value as String
        String option = getValue(propertyName);
        if (option == null) {
            logger.error("No default for property {}", propertyName);
            return null;
        }
        // look up object
        T t = (T) PROPERTY_MAP.get(propertyName).get(option);
        if (t == null) {
            logger.error("Unrecognized option for {}: {}", propertyName, option);
            return null;
        }
        return t;
    }

    String getName() { return name; }

    Set<String> getPropertyNames() {
        return PROPERTY_MAP.keySet();
    }

    Set<String> getOptions(String propertyName) {
        if (!PROPERTY_MAP.containsKey(propertyName)) {
            logger.error("Unrecognized property name {}", propertyName);
            return null;
        }
        return PROPERTY_MAP.get(propertyName).keySet();
    }

    String getValue(String propertyName) {
        return properties.getProperty(propertyName);
    }

    long getLastUpdated() {
        long lastUpdated = INDEX_NEVER_CREATED;
        String lastUpdatedStr = properties.getProperty("last.updated");
        if (lastUpdatedStr != null) {
            try {
                lastUpdated = Long.parseLong(lastUpdatedStr);
            } catch (NumberFormatException ex) {
                logger.error("Unable to determine time of last update", ex);
                lastUpdated = INDEX_INVALIDATED;
                invalidateIndex();
            }
        }
        return lastUpdated;
    }

    String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(getName());
        sb.append("]: ");
        sb.append(getValue("directory.type"));
        sb.append(" / Lucene ");
        sb.append(getValue("lucene.version"));
        sb.append(" / Analyzer: ");
        sb.append(getValue("lucene.analyzer"));
        sb.append(" / Scoring: ");
        sb.append(getValue("scoring.model"));
        return sb.toString();
    }

    String getStatus() {
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

    boolean isResolved() { return resolved; }
    String getHashAlgorithm() { return hashAlgorithm; }
    Version getVersion() { return version; }
    Analyzer getAnalyzer() { return analyzer; }
    Similarity getSimilarity() { return similarity; }
    Directory getDirectory() { return directory; }

    void set(String propertyName, String value) {
        if (resolved) {
            logger.error("Cannot set properties on resolved config");
            return;
        }
        // calling set invalidates index
        invalidateIndex();
        properties.setProperty(propertyName, value);
    }

    void setLastUpdated(long value) {
        properties.setProperty("last.updated", Long.toString(value));
    }

    void close() {
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException ex) {
                logger.error("I/O exception while closing index", ex);
            }
        }
        directory = null;
    }

    private void invalidateIndex() {
        if (!Long.toString(INDEX_NEVER_CREATED).equals(properties.getProperty("last.updated"))) {
            setLastUpdated(INDEX_INVALIDATED);
        }
    }

    void deleteIndex() {
        try {
            deltree(new File(configDir.getPath() + File.separator + INDEX_DIR));
            setLastUpdated(INDEX_NEVER_CREATED);
        } catch (IOException ex) {
            logger.error("Could not delete index", ex);
        }
    }

    void delete() {
        try {
            deltree(configDir);
        } catch (IOException ex) {
            logger.error("Could not delete configuration", ex);
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
}
