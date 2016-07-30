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

import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;

class Docket {
    // sentinel object: all files completed
    static Docket DONE = new Docket("", "", Status.PASS);

    static enum Status {
        SELECTED,   // selected by SelectTask
        PARSED,     // parsed by ParseTask
        PASS,       // pass through (no update required)
        DELETE      // delete entry from index
    }

    final String relPath;
    final String hashSum;
    ContentHandler content;
    Metadata metadata;
    Status status;

    Docket(String relPath, String hashSum, Status status) {
        this.relPath = relPath;
        this.hashSum = hashSum;
        this.content = null;
        this.metadata = null;
        this.status = status;
    }
}
