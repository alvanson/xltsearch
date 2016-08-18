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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import javax.xml.bind.DatatypeConverter;
import javafx.concurrent.Task;

class SelectTask extends Task<Boolean> {
    private final File root;
    private final Config config;
    private final BlockingQueue<Docket> outQueue;

    private final Logger logger = LoggerFactory.getLogger(SelectTask.class);

    SelectTask(File root, Config config, BlockingQueue<Docket> outQueue) {
        this.root = root;
        this.config = config;
        this.outQueue = outQueue;
    }

    @Override
    protected Boolean call() {
        boolean result = false;

        updateMessage("started");
        try {
            int count = 0;
            List<String> files = listFiles();
            Map<String,String> hashSums = getHashSums();
            long workLeft = Math.max(files.size(), hashSums.size());    // close enough
            // avoid repeatedly recreating digest object and bytes array
            MessageDigest digest = MessageDigest.getInstance(config.getHashAlgorithm());
            byte[] bytes = new byte[8192];
            // select files
            for (String relPath : files) {
                count++;
                workLeft--;
                updateMessage(relPath);
                File file = new File(root.getPath() + File.separator + relPath);
                String hashSum = computeHashSum(file, digest, bytes);
                // compare hash
                if (!hashSum.equals(hashSums.get(relPath))) {
                    outQueue.put(new Docket(relPath, hashSum, Docket.Status.SELECTED, workLeft));
                } else {    // hashes are the same
                    outQueue.put(new Docket(relPath, hashSum, Docket.Status.PASS, workLeft));
                }
                // remove from map (see below)
                hashSums.remove(relPath);
                updateProgress(count, count + workLeft);
            }
            // delete nonexistent files from index (those not removed above)
            workLeft = hashSums.keySet().size();
            for (String relPath : hashSums.keySet()) {
                count++;
                workLeft--;
                updateMessage("Deleting" + relPath);
                outQueue.put(new Docket(relPath, "", Docket.Status.DELETE, workLeft));
                updateProgress(count, count + workLeft);
            }
            // done
            updateMessage("complete");
            updateProgress(count, count + workLeft);
            outQueue.put(Docket.DONE);
            result = true;
        } catch (NoSuchAlgorithmException ex) {
            updateMessage("exception");
            logger.error("No such algorithm: {}", config.getHashAlgorithm(), ex);
        } catch (InterruptedException ex) {
            if (isCancelled()) {
                updateMessage("cancelled");
            } else {
                updateMessage("interrupted");
                logger.error("Interrupted", ex);
            }
        }
        return result;
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
            if (!name.equals(Catalog.CATALOG_DIR)) {  // don't index the catalog
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

    private Map<String,String> getHashSums() {
        Map<String,String> hashSums = new HashMap<>();
        DirectoryReader ireader = null;
        try {
            if (DirectoryReader.indexExists(config.getDirectory())) {
                // read hashsums from `directory`
                ireader = DirectoryReader.open(config.getDirectory());
                IndexSearcher isearcher = new IndexSearcher(ireader);
                Query query = new MatchAllDocsQuery();
                ScoreDoc[] hits = isearcher.search(query, ireader.numDocs()+1).scoreDocs;
                // collect results
                for (ScoreDoc hit : hits) {
                    Document document = isearcher.doc(hit.doc);
                    String relPath = document.get(config.pathField);
                    String hashSum = document.get(config.hashSumField);
                    if (relPath != null && hashSum != null) {
                        hashSums.put(relPath, hashSum);
                    }
                }
            }   // else: return empty map
        } catch (IOException ex) {
            logger.error("I/O exception while reading index", ex);
        }
        if (ireader != null) {
            try {
                ireader.close();
            } catch (IOException ex) {
                logger.warn("I/O exception while closing index reader", ex);
            }
        }
        return hashSums;
    }

    private String computeHashSum(File file, MessageDigest digest, byte[] bytes) {
        String hashSum = "";
        int bytesRead;
        try (FileInputStream stream = new FileInputStream(file)) {
            digest.reset();
            while ((bytesRead = stream.read(bytes)) != -1) {
                digest.update(bytes, 0, bytesRead);
            }
            hashSum = DatatypeConverter.printHexBinary(digest.digest());
        } catch (IOException ex) {
            logger.warn("I/O exception while processing {}", file, ex);
        }
        return hashSum;
    }
}
