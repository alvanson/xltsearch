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
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import javax.xml.bind.DatatypeConverter;
import javafx.concurrent.Task;

class SelectTask extends Task<Boolean> {
    private final File root;
    private final List<String> files;
    private final String algorithm;
    private final Directory directory;
    private final IndexFields indexFields;
    private final BlockingQueue<Docket> outQueue;
    private final int n;

    private final Logger logger = LoggerFactory.getLogger(SelectTask.class);

    SelectTask(File root, List<String> files, String algorithm, Directory directory,
            IndexFields indexFields, BlockingQueue<Docket> outQueue, int n) {
        this.root = root;
        this.files = files;
        this.algorithm = algorithm;
        this.directory = directory;
        this.indexFields = indexFields;
        this.outQueue = outQueue;
        this.n = n;
    }

    @Override
    protected Boolean call() {
        boolean result = false;
        int count = 0;

        updateMessage("started");
        updateProgress(0, n);

        try {
            Map<String,String> hashSums = getHashSums();
            // avoid repeatedly recreating digest object and bytes array
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = new byte[8192];
            // select files
            for (String relPath : files) {
                updateMessage(relPath);
                File file = new File(root.getPath() + File.separator + relPath);
                String hashSum = computeHashSum(file, digest, bytes);
                // compare hash
                if (!hashSum.equals(hashSums.get(relPath))) {
                    outQueue.put(new Docket(relPath, hashSum, Docket.Status.SELECTED));
                } else {    // hashes are the same
                    outQueue.put(new Docket(relPath, hashSum, Docket.Status.PASS));
                }
                // remove from map (see below)
                hashSums.remove(relPath);
                updateProgress(++count, n);
            }
            // delete nonexistent files from index (those not removed above)
            for (String relPath : hashSums.keySet()) {
                updateMessage("Deleting" + relPath);
                outQueue.put(new Docket(relPath, "", Docket.Status.DELETE));
            }
            // done
            updateMessage("complete");
            updateProgress(n, n);
            outQueue.put(Docket.DONE);
            result = true;
        } catch (NoSuchAlgorithmException ex) {
            updateMessage("exception");
            logger.error("No such algorithm: {}", algorithm, ex);
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

    private Map<String,String> getHashSums() {
        Map<String,String> hashSums = new HashMap<>();
        DirectoryReader ireader = null;
        try {
            if (DirectoryReader.indexExists(directory)) {
                // read hashsums from `directory`
                ireader = DirectoryReader.open(directory);
                IndexSearcher isearcher = new IndexSearcher(ireader);
                Query query = new MatchAllDocsQuery();
                ScoreDoc[] hits = isearcher.search(query, ireader.numDocs()+1).scoreDocs;
                // collect results
                for (ScoreDoc hit : hits) {
                    Document document = isearcher.doc(hit.doc);
                    String relPath = document.get(indexFields.path);
                    String hashSum = document.get(indexFields.hashSum);
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
