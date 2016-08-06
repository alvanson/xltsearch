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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.concurrent.Task;

class SearchTask extends Task<List<SearchResult>> {
    private final File root;
    private final Version version;
    private final Analyzer analyzer;
    private final Directory directory;
    private final IndexFields indexFields;
    private final String qstr;
    private final int limit;

    private final Logger logger = LoggerFactory.getLogger(SearchTask.class);

    SearchTask(File root, Version version, Analyzer analyzer, Directory directory,
            IndexFields indexFields, String qstr, int limit) {
        this.root = root;
        this.version = version;
        this.analyzer = analyzer;
        this.directory = directory;
        this.indexFields = indexFields;
        this.qstr = qstr;
        this.limit = limit;
    }

    @Override
    protected List<SearchResult> call() {
        DirectoryReader ireader = null;
        List<SearchResult> results = null;

        updateMessage("Searching...");
        try {
            ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);
            QueryParser parser = new QueryParser(version, indexFields.content, analyzer);
            Query query = parser.parse(qstr);
            ScoreDoc[] hits = isearcher.search(query, limit).scoreDocs;
            // collect results
            results = new ArrayList<>(hits.length);
            for (ScoreDoc hit : Arrays.asList(hits)) {
                Document document = isearcher.doc(hit.doc);
                File file = new File(
                    root.getPath() + File.separator + document.get(indexFields.path));
                String title = document.get(indexFields.title);
                if (title == null) {
                    title = "";
                }
                // report metadata in `moreInfo`
                StringBuilder sb = new StringBuilder();
                for (IndexableField field : document.getFields()) {
                    if (field.stringValue() != null) {
                        sb.append(field.name() + ": " + field.stringValue() + '\n');
                    }
                }
                results.add(new SearchResult(file, title, hit.score, sb.toString()));
            }
            updateMessage(hits.length + " results");
        } catch (IOException ex) {
            updateMessage("I/O exception");
            logger.error("I/O exception while reading index", ex);
        } catch (ParseException ex) {
            updateMessage("Parse error");
            logger.warn("Parse exception while parsing '{}'", qstr, ex);
        }
        // close ireader
        if (ireader != null) {
            try {
                ireader.close();
            } catch (IOException ex) {
                logger.warn("I/O exception while closing index reader", ex);
            }
        }
        return results;
    }
}
