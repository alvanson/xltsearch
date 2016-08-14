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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import javafx.concurrent.Task;

class ParseTask extends Task<Boolean> {
    private final File root;
    private final BlockingQueue<Docket> inQueue;
    private final BlockingQueue<Docket> outQueue;

    private final Logger logger = LoggerFactory.getLogger(ParseTask.class);

    ParseTask(File root, BlockingQueue<Docket> inQueue, BlockingQueue<Docket> outQueue) {
        this.root = root;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    @Override
    protected Boolean call() {
        boolean result = false;

        updateMessage("started");
        try {
            int count = 0;
            Docket docket;
            while ((docket = inQueue.take()) != Docket.DONE) {
                count++;
                updateMessage(docket.relPath);
                switch (docket.status) {
                    case SELECTED:
                        // index selected file
                        AutoDetectParser parser = new AutoDetectParser();
                        docket.content = new BodyContentHandler(-1);  // unlimited chars
                        docket.metadata = new Metadata();
                        // pass filename to parser as hint to document format
                        docket.metadata.set(Metadata.RESOURCE_NAME_KEY, docket.relPath);
                        try (FileInputStream stream = new FileInputStream(
                                root.getPath() + File.separator + docket.relPath)) {
                            // parse file
                            parser.parse(stream, docket.content, docket.metadata);
                            docket.status = Docket.Status.PARSED;
                        } catch (IOException ex) {
                            docket.status = Docket.Status.PASS;
                            logger.warn("I/O exception while processing {}", docket.relPath, ex);
                        } catch (SAXException ex) {
                            docket.status = Docket.Status.PASS;
                            logger.warn("SAX exception while processing {}", docket.relPath, ex);
                        } catch (TikaException ex) {
                            docket.status = Docket.Status.PASS;
                            logger.warn("Tika exception while processing {}", docket.relPath, ex);
                        }
                        // fall through
                    case PASS:    // fall through
                    case DELETE:  // fall through
                        outQueue.put(docket);
                        updateProgress(count, count + docket.workLeft);
                        break;
                    default:
                        logger.error("Unexpected docket state while processing {}: {}",
                            docket.relPath, docket.status.toString());
                        outQueue.put(Docket.DONE);
                        cancel(true);   // cancel task
                }
            }
            // end of queue
            updateMessage("complete");
            updateProgress(count, count + docket.workLeft);
            outQueue.put(docket);   // == Docket.DONE
            result = true;
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
