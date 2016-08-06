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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import javax.xml.bind.DatatypeConverter;
import javafx.concurrent.Task;

class SelectTask extends Task<Boolean> {
    private final File root;
    private final List<String> files;
    private final String algorithm;
    private final Map<String,String> hashSums;
    private final BlockingQueue<Docket> outQueue;
    private final int n;

    private final Logger logger = LoggerFactory.getLogger(SelectTask.class);

    SelectTask(File root, List<String> files, String algorithm, Map<String,String> hashSums,
            BlockingQueue<Docket> outQueue, int n) {
        this.root = root;
        this.files = files;
        this.algorithm = algorithm;
        this.hashSums = hashSums;
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
            // avoid repeatedly recreating digest object and bytes array
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = new byte[8192];
            int bytesRead;
            // select files
            for (String relPath : files) {
                updateMessage(relPath);
                try (FileInputStream stream =
                        new FileInputStream(root.getPath() + File.separator + relPath)) {
                    // compute hash
                    digest.reset();
                    // block reads are MUCH faster than byte-at-a-time read()
                    while ((bytesRead = stream.read(bytes)) != -1) {
                        digest.update(bytes, 0, bytesRead);
                    }
                    String hashSum = DatatypeConverter.printHexBinary(digest.digest());
                    // compare hash
                    if (!hashSum.equals(hashSums.get(relPath))) {
                        outQueue.put(new Docket(relPath, hashSum, Docket.Status.SELECTED));
                    } else {    // same
                        outQueue.put(new Docket(relPath, hashSum, Docket.Status.PASS));
                    }
                } catch (IOException ex) {
                    logger.warn("I/O exception while processing {}", relPath, ex);
                }
                updateProgress(++count, n);
            }
            // delete nonexistent files from index
            Set<String> difference = new HashSet<>(hashSums.keySet());
            difference.removeAll(files);    // files in hashSums but no longer in root
            for (String relPath : difference) {
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
}
