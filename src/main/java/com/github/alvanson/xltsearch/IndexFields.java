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

import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class IndexFields {
    private static final String CONTENT = "content";
    private static final Map<String,Property> METADATA =
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
    private static final String PATH = "path";
    private static final String TITLE = "title";
    private static final String HASHSUM = "hashsum";

    final String content;
    final Map<String,Property> metadata;
    final String path;
    final String title;
    final String hashSum;

    IndexFields() {
        this.content = CONTENT;
        this.metadata = METADATA;
        this.path = PATH;
        this.title = TITLE;
        this.hashSum = HASHSUM;
    }
}
