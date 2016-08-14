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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.apache.tika.metadata.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import javafx.concurrent.Task;

class IndexTask extends Task<Boolean> {
    private final BlockingQueue<Docket> inQueue;
    private final Version version;
    private final Analyzer analyzer;
    private final Similarity similarity;
    private final Directory directory;
    private final IndexFields indexFields;

    private final Logger logger = LoggerFactory.getLogger(IndexTask.class);

    IndexTask(BlockingQueue<Docket> inQueue, Version version, Analyzer analyzer,
            Similarity similarity, Directory directory, IndexFields indexFields) {
        this.inQueue = inQueue;
        this.version = version;
        this.analyzer = analyzer;
        this.similarity = similarity;
        this.directory = directory;
        this.indexFields = indexFields;
    }

    @Override
    protected Boolean call() {
        IndexWriter iwriter = null;
        boolean result = false;

        updateMessage("started");
        try {
            int count = 0;
            Docket docket;

            IndexWriterConfig config = new IndexWriterConfig(version, analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setSimilarity(similarity);
            iwriter = new IndexWriter(directory, config);

            while ((docket = inQueue.take()) != Docket.DONE) {
                count++;
                updateMessage(docket.relPath);
                switch (docket.status) {
                    case PARSED:
                        // index parsed file
                        Document doc = new Document();
                        // store relative path  ** must be indexed for updateDocument
                        doc.add(new StringField(indexFields.path,
                            docket.relPath, Field.Store.YES));
                        // index content
                        doc.add(new TextField(indexFields.content,
                            docket.content.toString(), Field.Store.NO));
                        // index standard metadata
                        for (Map.Entry<String,Property> e : indexFields.metadata.entrySet()) {
                            for (String value : docket.metadata.getValues(e.getValue())) {
                                doc.add(new TextField(e.getKey(), value, Field.Store.YES));
                            }
                        }
                        // store hashsum
                        doc.add(new StringField(indexFields.hashSum,
                            docket.hashSum, Field.Store.YES));
                        // add/update document
                        iwriter.updateDocument(new Term(indexFields.path, docket.relPath), doc);
                        // fall through
                    case PASS:
                        break;
                    case DELETE:
                        iwriter.deleteDocuments(new Term(indexFields.path, docket.relPath));
                        break;
                    default:
                        logger.error("Unexpected docket state while processing {}: {}",
                            docket.relPath, docket.status.toString());
                        cancel(true);   // cancel task
                }
                updateProgress(count, count + docket.workLeft);
            }
            // end of queue
            updateMessage("complete");
            updateProgress(count, count + docket.workLeft);
            result = true;
        } catch (IOException ex) {
            updateMessage("I/O exception");
            logger.error("I/O exception while writing to index", ex);
        } catch (InterruptedException ex) {
            if (isCancelled()) {
                updateMessage("cancelled");
            } else {
                updateMessage("interrupted");
                logger.error("Interrupted", ex);
            }
        }
        // close iwriter
        if (iwriter != null) {
            try {
                iwriter.close();
            } catch (IOException ex) {
                logger.warn("I/O exception while closing index writer", ex);
            }
        }
        return result;
    }
}
